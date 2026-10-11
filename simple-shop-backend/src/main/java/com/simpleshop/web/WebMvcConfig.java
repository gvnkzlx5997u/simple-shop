package com.simpleshop.web;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.simpleshop.config.AppProperties;

/**
 * Web MVC 配置：注册卖家端鉴权拦截器、映射商品图片的静态资源路径、启用定时任务。
 *
 * <h2>本类做的三件事</h2>
 * <ol>
 *   <li><b>注册 {@link SellerAuthInterceptor}</b>——只拦 {@code /api/seller/**}，
 *       并<b>排除会话端点</b> {@link #SESSION_PATH}（{@code I11-01} 登录不需要鉴权，
 *       {@code 11.3.2}；{@code I11-02} 退出登录须幂等且不得抛 401）。买家端 JSP 端点
 *       （{@code B11-01} ~ {@code B11-07}）属下一轮，
 *       届时<b>不得</b>纳入本拦截器——买家端<b>不需要鉴权</b>（凭证是口令码，{@code O-01}、{@code BR-32}）。</li>
 *   <li><b>映射图片静态资源</b>——把配置的 {@code url-prefix}（默认 {@code /images}）
 *       映射到文件系统上的 {@code directory}（默认 {@code ./data/images}）。
 *       依据 {@code C-08}、{@code A-06}、{@code DEC-09}：图片存服务器文件系统、库内只存路径。</li>
 *   <li><b>启用定时任务</b>（{@code @EnableScheduling}）——供 {@code SessionStore.purgeExpired()}
 *       清理过期会话。放在本类而不是主类，是为了保持
 *       {@code SimpleShopApplication} 作为纯粹入口的形态（方案附录 B 的不改动倾向）。</li>
 * </ol>
 *
 * <h2>⚠️ 静态资源映射与「不限流」的边界</h2>
 * <p>商品图片是<b>公开可访问</b>的——买家端展示商品详情本就要看到图片（{@code FR-12}、{@code FR-19}），
 * 因此 {@code /images/**} <b>不</b>纳入鉴权拦截器，这是<b>有意为之</b>，不是遗漏。
 * 但要注意：图片路径<b>不可猜测</b>（服务端生成 UUID 文件名，见方案 §6），
 * 否则会成为「枚举他人商品图」的探测面。
 *
 * <h2>⚠️ 路径穿越防护</h2>
 * <p>{@code /images/**} 由 Spring 的 {@code ResourceHttpRequestHandler} 处理，
 * 它<b>自带</b>路径归一化与目录穿越防护。但仍<b>不可</b>让用户可控字符串直接参与
 * 文件系统拼接——{@code I11-06} 的 {@code pic_url} 回填校验必须用
 * {@link AppProperties.Image#directoryPath()} 归一化后比对，见方案 §6。
 */
@Configuration
@EnableScheduling
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    /** 卖家端接口前缀。 */
    public static final String SELLER_API_PREFIX = "/api/seller/**";

    /**
     * 会话端点路径，须从鉴权拦截器中排除。
     *
     * <p><b>⚠️ 这一个路径承载两个接口</b>：{@code I11-01}（{@code POST}，登录，不需要鉴权）
     * 与 {@code I11-02}（{@code DELETE}，退出登录，幂等、同样不得抛 401）。
     * 故常量名用中性的 {@code SESSION_PATH} 而不是 {@code LOGIN_PATH}——
     * 后者会让人以为这里只有登录，进而在「为什么退出登录也不被校验」上重新推一遍。
     *
     * <p><b>⚠️ 本常量是 Controller 映射的唯一来源</b>：{@code SellerSessionController}
     * 的 {@code @RequestMapping} 直接引用它。这是有意为之——
     * 若两边各写一个字符串字面量，一旦有人只改了其中一个（把接口挪到
     * {@code /api/seller/login} 之类），登录接口就会落进拦截器的覆盖范围而被
     * {@code 10002} 拒掉，直接形成<b>无法登录的死锁</b>（登录需要会话、会话需要登录）。
     * 共享常量把这种错误变成不可能。
     */
    public static final String SESSION_PATH = "/api/seller/session";

    private final SellerAuthInterceptor sellerAuthInterceptor;
    private final AppProperties properties;

    public WebMvcConfig(SellerAuthInterceptor sellerAuthInterceptor, AppProperties properties) {
        this.sellerAuthInterceptor = sellerAuthInterceptor;
        this.properties = properties;
    }

    /**
     * 注册鉴权拦截器。
     *
     * <p>{@code excludePathPatterns} 用<b>完整路径</b>而非通配：
     * {@code I11-01}（{@code POST}）与 {@code I11-02}（{@code DELETE}）<b>共用同一路径</b>
     * {@code /api/seller/session}。因此排除的是<b>路径</b>，不是「某个方法」——
     * 这会造成一个副作用：{@code DELETE /api/seller/session}（退出登录）也<b>不会</b>被本拦截器拦住。
     *
     * <p><b>⚠️ 这个副作用是安全的，但必须由 {@code I11-02} 自己兜住</b>（S3 已落实并有用例）：
     * {@code SellerSessionController#logout} <b>不依赖</b>本拦截器去解析 token，
     * 而是自己从 {@code Authorization} 头取出并移除它；token 无效时只是<b>幂等无操作</b>
     * （{@code SessionStore.remove} 不会报错），<b>不会</b>泄漏任何信息，也<b>不会</b>返回 401。
     * 契约对 {@code I11-02} 列出的错误码本来就只有 {@code 10002}「会话已失效」，
     * 而幂等成功同样可接受。
     * 若将来希望退出登录也严格校验 token，应改为按「路径 + 方法」精确排除——
     * 那需要自定义 {@code HandlerInterceptor} 内的判断，而不是 {@code excludePathPatterns}。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sellerAuthInterceptor)
                .addPathPatterns(SELLER_API_PREFIX)
                .excludePathPatterns(SESSION_PATH)
                .order(0);
    }

    /**
     * 映射商品图片：{@code url-prefix}/** → 文件系统 {@code directory}/。
     *
     * <p>末尾的 {@code /} 不可省：{@code ResourceHandlerRegistry} 要求 location 以分隔符结尾，
     * 否则相对路径拼接会出错。这里用 {@link Path} 归一化后补上。
     *
     * <p>目录<b>不存在也可以</b>——Spring 只是在请求时找不到文件返回 404。
     * 但为了让运维早点发现挂载问题（尤其是容器化时<b>忘记挂持久卷</b>，方案 §5.3 第 1 条），
     * 启动时检测一次并给出<b>警告</b>（不阻断启动：首次发布商品前的空目录是正常状态）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        AppProperties.Image image = properties.getImage();
        String prefix = image.getUrlPrefix();
        // 归一化并补尾部分隔符
        String location = image.directoryPath().toString();
        if (!location.endsWith(java.io.File.separator)) {
            location = location + java.io.File.separator;
        }
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations("file:" + location);

        if (!image.directoryPath().toFile().isDirectory()) {
            log.warn("image directory does not exist yet: {} (url prefix: {}). "
                            + "It will be created on first upload; if running in a container, "
                            + "make sure a persistent volume is mounted here (plan §5.3).",
                    image.directoryPath(), prefix);
        } else {
            log.info("serving product images: {} -> {}", prefix + "/**", location);
        }
    }
}
