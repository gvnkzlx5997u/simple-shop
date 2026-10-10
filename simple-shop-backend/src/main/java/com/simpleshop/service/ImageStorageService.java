package com.simpleshop.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.simpleshop.config.AppProperties;
import com.simpleshop.persistence.time.DatabaseTimeProvider;
import com.simpleshop.service.dto.ImageData;
import com.simpleshop.service.exception.BusinessException;
import com.simpleshop.service.exception.ErrorCode;
import com.simpleshop.service.support.ImageType;

/**
 * 商品图片的落盘与路径回填校验（{@code I11-05}，方案 §6）。
 *
 * <p>依据：{@code C-08}（图片存服务器文件系统）、{@code C-14}（JPG/PNG、≤5MB）、
 * {@code A-06}（{@code /images/**} 映射）、{@code DEC-09}（库内只存路径，不存二进制）、
 * {@code DEC-34}（不生成缩略图）、{@code NFR-11}（前后端双重校验）。
 *
 * <h2>落盘布局</h2>
 * <pre>
 * &lt;simple-shop.image.directory&gt;/&lt;uuuu&gt;/&lt;MM&gt;/&lt;uuid&gt;.&lt;jpg|png&gt;
 *   例：./data/images/2026/10/3f2b...-....jpg
 * 对外 URL：&lt;url-prefix&gt;/&lt;uuuu&gt;/&lt;MM&gt;/&lt;uuid&gt;.&lt;jpg|png&gt;
 *   例：/images/2026/10/3f2b...-....jpg        ← 直接写进 goods.pic_url
 * </pre>
 * <p>按日期分子目录是为了让单个目录的文件数不随时间无限增长（方案 §6「目录策略」）。
 *
 * <h2>⚠️ 文件名完全由服务端生成</h2>
 * <p>不使用用户原始文件名（方案 §6）。原始文件名是不可信输入：它可能含路径分隔符
 * （{@code ../}）、空字节、大小写敏感/不敏感的差异、或与已有文件同名导致<b>静默覆盖</b>。
 * 这里用 {@link UUID} + <b>服务端识别出的</b>扩展名，把「文件名」这一个攻击面整体消掉。
 *
 * <h2>⚠️ 日期子目录用 UTC</h2>
 * <p>目录名取 {@link DatabaseTimeProvider}（全项目唯一时间来源）的 UTC 墙上时间。
 * 开发机是 UTC+8，若这里用了 {@code LocalDateTime.now()}，跨零点上传的文件会落到错误日期目录，
 * 而路径已写进 {@code pic_url}——之后没人能靠肉眼发现错在哪。
 *
 * <h2>⚠️ 写盘失败不返回 {@code 20009}</h2>
 * <p>磁盘满、权限不足这类 {@link IOException} 是<b>服务端故障</b>，不是「图片不合规」。
 * 若把它们都报成 {@code 20009}（图片格式/大小不合规），前端会提示用户「仅支持 JPG/PNG」——
 * 用户换一百张图都没用。故此处包成 {@link UncheckedIOException} 向上抛，
 * 由 {@code GlobalExceptionHandler} 的兜底分支返回 {@code 500} + {@code 50000}
 * （也是 {@code BusinessException} 类注释第 3 条的要求：不用业务异常表达程序缺陷）。
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    /**
     * 单张图片大小上限：5MB（{@code C-14}）。
     *
     * <p>⚠️ 这是<b>第二道</b>防线。第一道是 {@code spring.servlet.multipart.max-file-size}
     * （Spring 在进 Controller 前就抛 {@code MaxUploadSizeExceededException} → {@code 20009}）。
     * 两道都要有：{@code NFR-11} 的可测指标明确要求「<b>后端独立校验</b>」——
     * 若只有 Spring 那道，一旦有人调大或去掉该配置，校验就凭空消失且无人察觉。
     */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    /** 读多少字节即可判定类型：PNG 的魔数最长，为 8 字节。 */
    private static final int MAGIC_PROBE_BYTES = 8;

    /** 日期子目录格式（{@code uuuu} 而非 {@code yyyy}：避免 {@code yyyy} 在严格解析下的纪元歧义）。 */
    private static final DateTimeFormatter DATE_DIRECTORY = DateTimeFormatter.ofPattern("uuuu/MM");

    private final Path rootDirectory;
    private final String urlPrefix;
    private final DatabaseTimeProvider timeProvider;

    /**
     * {@code pic_url} 的<b>严格</b>形状：由本服务生成过的合法相对路径（方案 §6 的「回填校验」）。
     *
     * <p>由配置的 {@code url-prefix} 拼出（不写死 {@code /images}），并按 {@code Pattern.quote}
     * 转义——前缀来自配置，若含正则元字符（如 {@code /img.v1}）会静默改变匹配语义。
     *
     * <p>分组：1=年、2=月、3=UUID、4=扩展名。这三段只用于<b>重新拼回文件路径</b>，
     * 不接受任何其它形状——因此 {@code ../../etc/passwd}、绝对路径、{@code <script>} 全在此被挡下。
     */
    private final Pattern picUrlPattern;

    public ImageStorageService(AppProperties properties, DatabaseTimeProvider timeProvider) {
        this.rootDirectory = properties.getImage().directoryPath();
        this.urlPrefix = properties.getImage().getUrlPrefix();
        this.timeProvider = timeProvider;
        this.picUrlPattern = Pattern.compile(
                "^" + Pattern.quote(urlPrefix)
                        + "/(\\d{4})/(\\d{2})/"
                        + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                        + "\\.(jpg|png)$");
    }

    // -------------------------------------------------------------------------
    // I11-05 上传
    // -------------------------------------------------------------------------

    /**
     * 校验并保存上传的图片，返回可写入 {@code goods.pic_url} 的相对路径。
     *
     * <h2>校验顺序（先便宜后昂贵）</h2>
     * <ol>
     *   <li>空文件 → {@code 20009}；</li>
     *   <li>大小超过 5MB → {@code 20009}（先看 {@code getSize()} 声明的长度，<b>不</b>先读内容）；</li>
     *   <li>读开头 8 字节判魔数 → 不是 JPG/PNG 则 {@code 20009}。</li>
     * </ol>
     * <p>第 2 步用 {@code MultipartFile.getSize()} 而不是「把文件读完再量」：
     * 内容已在磁盘/内存的临时区，量长度是常数代价，而「先读 100MB 再拒绝」等于把资源消耗前置。
     *
     * @param file multipart 中字段名为 {@code file} 的部分
     * @return {@code {pic_url}}，含配置的 URL 前缀
     * @throws BusinessException {@code 20009}——空文件、超过 5MB、或内容不是 JPG/PNG
     * @throws UncheckedIOException 写盘失败（服务端故障，最终为 {@code 500}）
     */
    public ImageData store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            log.warn("rejected oversized upload: declaredSize={} limit={}", file.getSize(), MAX_IMAGE_BYTES);
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        ImageType type = ImageType.detect(readMagicBytes(file))
                .orElseThrow(() -> {
                    // 只记声明的大小与原始文件名，不记内容；文件名本就不可信，此处仅用于排查
                    log.warn("rejected upload with unsupported content type: declaredSize={}", file.getSize());
                    return new BusinessException(ErrorCode.IMAGE_INVALID);
                });

        // ⚠️ 时间只能来自 DatabaseTimeProvider（全项目唯一来源，方案 §3.1）。
        //    它返回的是 UTC 墙上时间，故直接用 uuuu/MM 格式化即可——不经任何时区转换。
        String datePath = DATE_DIRECTORY.format(timeProvider.nowUtc());
        String fileName = UUID.randomUUID() + "." + type.extension();
        Path targetDirectory = rootDirectory.resolve(datePath);
        Path target = targetDirectory.resolve(fileName).normalize();

        // 路径穿越的最后一道断言：上面三段都是本方法生成的（日期来自时钟、名字来自 UUID），
        // 理论上不可能越界；但它是「万一将来有人改成拼接用户输入」时的护栏，代价近乎为零。
        if (!target.startsWith(rootDirectory)) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }

        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(target);
        } catch (IOException e) {
            // 服务端故障，不是「图片不合规」——见类注释
            throw new UncheckedIOException("failed to store uploaded image at " + target, e);
        }

        String picUrl = urlPrefix + "/" + datePath + "/" + fileName;
        log.info("stored product image: type={} bytes={} picUrl={}", type, file.getSize(), picUrl);
        return ImageData.of(picUrl);
    }

    // -------------------------------------------------------------------------
    // I11-06 的 pic_url 回填校验
    // -------------------------------------------------------------------------

    /**
     * 校验 {@code pic_url} 确实指向本服务落盘过的图片（方案 §6）。
     *
     * <p>为什么必须校验：发布商品是 {@code pic_url} <b>唯一</b>的写入时机
     * （{@code BR-15} 之后不再允许修改），若这里放行任意字符串，
     * 库里就会出现外部 URL、{@code javascript:} 伪协议、或 {@code data:} 内联脚本这类值，
     * 而前端会把它直接放进 {@code <img src>}——那是存储型 XSS 的入口（{@code NFR-20}）。
     *
     * <p>两道检查缺一不可：
     * <ol>
     *   <li><b>形状</b>：必须精确匹配本服务的路径模板（正则）；</li>
     *   <li><b>存在</b>：该文件确实在图片目录里（防「猜一个合法形状的路径写进库」）。</li>
     * </ol>
     *
     * <p>⚠️ 刻意<b>不</b>复查文件魔数与扩展名是否一致：文件名是服务端按识别结果生成的，
     * 不一致只可能来自手工构造的 URL，而它已经通过了「形状 + 存在」两关，
     * 再读一次文件换不来安全收益。若将来允许用户替换图片文件，这条需重评。
     *
     * @param picUrl 待校验的相对路径；{@code null} 表示未上传图片，直接通过
     * @throws BusinessException {@code 20009}——形状不符或文件不存在
     */
    public void verifyStoredPicUrl(String picUrl) {
        if (picUrl == null) {
            return;
        }
        Matcher matcher = picUrlPattern.matcher(picUrl);
        if (!matcher.matches()) {
            log.warn("rejected pic_url with unexpected shape");
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        Path candidate = rootDirectory
                .resolve(matcher.group(1))
                .resolve(matcher.group(2))
                .resolve(matcher.group(3) + "." + matcher.group(4))
                .normalize();
        if (!candidate.startsWith(rootDirectory) || !Files.isRegularFile(candidate)) {
            log.warn("rejected pic_url pointing at a non-existent file");
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /**
     * 只读开头的若干字节用于类型判定。
     *
     * <p>{@code readNBytes} 在文件短于请求长度时返回实际读到的字节，不抛异常——
     * 短文件会在 {@code ImageType.detect} 里因「长度不足」被拒，无需在此特判。
     */
    private static byte[] readMagicBytes(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readNBytes(MAGIC_PROBE_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read uploaded image header", e);
        }
    }
}
