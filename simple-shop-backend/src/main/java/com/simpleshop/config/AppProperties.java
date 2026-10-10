package com.simpleshop.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * simple-shop 业务配置（前缀 {@code simple-shop}）。绑定 {@code application.yml} 中的对应项。
 *
 * <h2>为什么用 {@code @Component} 而不是 {@code @EnableConfigurationProperties}</h2>
 * <p>本类用 {@link Component} 注册为 Bean，因此<b>无需改动 {@code SimpleShopApplication}</b>
 * （数据层交付物不改动，见方案附录 B）。若将来新增更多配置类，可改为在主类上加
 * {@code @ConfigurationPropertiesScan}，本类把 {@code @Component} 去掉即可，<b>字段与用法不变</b>。
 *
 * <h2>取值的需求依据</h2>
 * <table border="1">
 *   <caption>配置项与依据</caption>
 *   <tr><th>配置项</th><th>依据</th></tr>
 *   <tr><td>{@code image.directory} / {@code image.url-prefix}</td>
 *       <td>{@code C-08}、{@code A-06}、{@code DEC-09}、{@code NFR-11}；方案 §6</td></tr>
 *   <tr><td>{@code session.timeout-minutes}</td>
 *       <td>{@code C-07}、{@code DEC-13}、{@code NFR-04}</td></tr>
 *   <tr><td>{@code session.timeout-tolerance-seconds}</td>
 *       <td>{@code 12-P7}（超时判断宽容 ±1 分钟）</td></tr>
 *   <tr><td>{@code queue.max-size}</td>
 *       <td>{@code DEC-23}、{@code NFR-07}</td></tr>
 *   <tr><td>{@code rate-limit.submit-intention-per-minute}</td>
 *       <td>{@code NFR-17}、{@code 12-P6}</td></tr>
 * </table>
 *
 * <h2>⚠️ 使用约束</h2>
 * <ul>
 *   <li>{@link #getQueue()}{@code .getMaxSize()} 是<b>上限值</b>，不是判定逻辑：
 *       是否拒绝由 Service 用 {@code IntentionRepository.countNonTerminal(...)} 的结果自行判定，
 *       且<b>必须在持有序号表行锁之后</b>计数（否则并发下可能突破上限）。</li>
 *   <li>{@link #getRateLimit()} 与「卖家登录不限失败次数」（{@code DEC-25}）、
 *       「口令码查询不限错误次数」（{@code DEC-12}）是<b>三处分列的独立策略</b>，不得互相套用。</li>
 *   <li>{@link #getSession()} 的实现为<b>进程内</b>（方案 §5）：
 *       后端为单容器时才成立。若改为多副本/负载均衡，必须回到方案 §5.4 复核。</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "simple-shop")
public class AppProperties {

    private final Image image = new Image();
    private final Session session = new Session();
    private final Queue queue = new Queue();
    private final RateLimit rateLimit = new RateLimit();

    public Image getImage() {
        return image;
    }

    public Session getSession() {
        return session;
    }

    public Queue getQueue() {
        return queue;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /** 图片存储与访问（{@code C-08}、{@code A-06}、{@code DEC-09}、{@code DEC-34}）。 */
    public static class Image {

        /** 服务器文件系统上的图片目录；数据库只存相对路径，不存二进制。 */
        private String directory = "./data/images";

        /** 对外访问前缀；由 WebMvcConfig 映射到 {@link #directory}。 */
        private String urlPrefix = "/images";

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getUrlPrefix() {
            return urlPrefix;
        }

        public void setUrlPrefix(String urlPrefix) {
            this.urlPrefix = urlPrefix;
        }

        /**
         * 图片目录的绝对路径形式，便于 Service 做「路径穿越」防护。
         *
         * <p>⚠️ Service 在拼接 {@code directory + 用户可控片段} 前，
         * 必须用本方法归一化后再校验「结果是否仍在目录内」，
         * 不得直接拼接（{@code I11-06} 的 {@code pic_url} 回填校验依赖它）。
         */
        public Path directoryPath() {
            return Path.of(directory).toAbsolutePath().normalize();
        }
    }

    /** 会话（{@code C-07}、{@code DEC-13}、{@code DEC-17}、{@code NFR-04}、{@code 12-P7}）。 */
    public static class Session {

        /** 无操作自动退出时长（滑动过期）。 */
        private Duration timeout = Duration.ofMinutes(30);

        /** 超时判定的宽容窗口（{@code 12-P7}：±1 分钟）。 */
        private Duration timeoutTolerance = Duration.ofSeconds(60);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getTimeoutTolerance() {
            return timeoutTolerance;
        }

        public void setTimeoutTolerance(Duration timeoutTolerance) {
            this.timeoutTolerance = timeoutTolerance;
        }
    }

    /** 队列容量（{@code DEC-23}、{@code NFR-07}）。 */
    public static class Queue {

        /** 单商品意向队列的<b>非终态</b>条数上限。 */
        private int maxSize = 1000;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    /** 限流（{@code NFR-17}、{@code 12-P6}）。 */
    public static class RateLimit {

        /** 提交购买意向：同一 IP 每分钟允许的次数。 */
        private int submitIntentionPerMinute = 10;

        public int getSubmitIntentionPerMinute() {
            return submitIntentionPerMinute;
        }

        public void setSubmitIntentionPerMinute(int submitIntentionPerMinute) {
            this.submitIntentionPerMinute = submitIntentionPerMinute;
        }
    }
}
