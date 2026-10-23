package com.simpleshop.common.media;

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

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 图片落盘与地址校验。
 *
 * <p>文件按 UTC 年/月分目录存放，文件名由服务端生成，数据库只存相对 URL。
 * 写盘失败属于服务端故障，向上抛出而不是报成图片不合规。
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private static final int MAGIC_PROBE_BYTES = 8;
    private static final DateTimeFormatter DATE_DIRECTORY = DateTimeFormatter.ofPattern("uuuu/MM");

    private final Path rootDirectory;
    private final String urlPrefix;
    private final DatabaseTimeProvider timeProvider;
    private final Pattern storedUrlPattern;

    public ImageStorageService(AppProperties properties, DatabaseTimeProvider timeProvider) {
        this.rootDirectory = properties.getImage().directoryPath();
        this.urlPrefix = properties.getImage().getUrlPrefix();
        this.timeProvider = timeProvider;
        this.storedUrlPattern = Pattern.compile(
                "^" + Pattern.quote(urlPrefix)
                        + "/(\\d{4})/(\\d{2})/"
                        + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                        + "\\.(jpg|png)$");
    }

    /**
     * 校验并保存图片，返回可直接写入数据库的相对 URL。
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            log.warn("rejected oversized upload: declaredSize={} limit={}", file.getSize(), MAX_IMAGE_BYTES);
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        ImageType type = ImageType.detect(readMagicBytes(file))
                .orElseThrow(() -> {
                    log.warn("rejected upload with unsupported content: declaredSize={}", file.getSize());
                    return new BusinessException(ErrorCode.IMAGE_INVALID);
                });

        String datePath = DATE_DIRECTORY.format(timeProvider.nowUtc());
        String fileName = UUID.randomUUID() + "." + type.extension();
        Path targetDirectory = rootDirectory.resolve(datePath);
        Path target = targetDirectory.resolve(fileName).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }

        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to store uploaded image at " + target, exception);
        }

        String url = urlPrefix + "/" + datePath + "/" + fileName;
        log.info("stored image: type={} bytes={} url={}", type, file.getSize(), url);
        return url;
    }

    /**
     * 校验 URL 确实指向本服务落盘过的图片。
     *
     * <p>同时检查形状与文件存在性，防止外部地址、伪协议或猜出的路径写入数据库。
     * {@code null} 表示未上传，直接通过。
     */
    public void verifyStoredUrl(String url) {
        if (url == null) {
            return;
        }
        Matcher matcher = storedUrlPattern.matcher(url);
        if (!matcher.matches()) {
            log.warn("rejected image url with unexpected shape");
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        Path candidate = rootDirectory
                .resolve(matcher.group(1))
                .resolve(matcher.group(2))
                .resolve(matcher.group(3) + "." + matcher.group(4))
                .normalize();
        if (!candidate.startsWith(rootDirectory) || !Files.isRegularFile(candidate)) {
            log.warn("rejected image url pointing at a non-existent file");
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
    }

    private static byte[] readMagicBytes(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readNBytes(MAGIC_PROBE_BYTES);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded image header", exception);
        }
    }
}
