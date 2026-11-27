package com.simpleshop.common.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 业务配置，前缀 {@code simple-shop}。
 *
 * <p>所有值都可以用环境变量覆盖，便于容器部署。
 */
@Component
@ConfigurationProperties(prefix = "simple-shop")
public class AppProperties {

    private final Media media = new Media();
    private final Session session = new Session();
    private final RateLimit rateLimit = new RateLimit();
    private final Pagination pagination = new Pagination();
    private final Payment payment = new Payment();
    private final Order order = new Order();

    public Media getMedia() {
        return media;
    }

    public Session getSession() {
        return session;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public Payment getPayment() {
        return payment;
    }

    public Order getOrder() {
        return order;
    }

    /** 媒体存储与上传限制。 */
    public static class Media {

        private String directory = "./data/media";
        private String urlPrefix = "/media";

        /** 商品主图/图集：只允许图片。 */
        private DataSize goodsImageMaxSize = DataSize.ofMegabytes(5);

        /** 描述内容块中的图片。目前与商品图片同值，但保留独立配置。 */
        private DataSize descriptionImageMaxSize = DataSize.ofMegabytes(5);

        /** 描述内容块中的视频。 */
        private DataSize descriptionVideoMaxSize = DataSize.ofMegabytes(20);

        /** 售后凭证图：与商品图同样是 5MB，但独立配置，将来可以单独调。 */
        private DataSize aftersaleImageMaxSize = DataSize.ofMegabytes(5);

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

        public DataSize getGoodsImageMaxSize() {
            return goodsImageMaxSize;
        }

        public void setGoodsImageMaxSize(DataSize goodsImageMaxSize) {
            this.goodsImageMaxSize = goodsImageMaxSize;
        }

        public DataSize getDescriptionImageMaxSize() {
            return descriptionImageMaxSize;
        }

        public void setDescriptionImageMaxSize(DataSize descriptionImageMaxSize) {
            this.descriptionImageMaxSize = descriptionImageMaxSize;
        }

        public DataSize getDescriptionVideoMaxSize() {
            return descriptionVideoMaxSize;
        }

        public DataSize getAftersaleImageMaxSize() {
            return aftersaleImageMaxSize;
        }

        public void setAftersaleImageMaxSize(DataSize aftersaleImageMaxSize) {
            this.aftersaleImageMaxSize = aftersaleImageMaxSize;
        }

        public void setDescriptionVideoMaxSize(DataSize descriptionVideoMaxSize) {
            this.descriptionVideoMaxSize = descriptionVideoMaxSize;
        }

        /** 归一化后的媒体目录绝对路径，用于目录穿越校验。 */
        public Path directoryPath() {
            return Path.of(directory).toAbsolutePath().normalize();
        }
    }

    public static class Session {

        private Duration timeout = Duration.ofMinutes(30);
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

    public static class RateLimit {

        /** 保护性限流总开关：开发与测试直接关掉，而不是把阈值调大。 */
        private boolean enabled = true;
        private int registerPerMinute = 50;
        private int loginPerMinute = 50;
        private int orderPerMinute = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRegisterPerMinute() {
            return registerPerMinute;
        }

        public void setRegisterPerMinute(int registerPerMinute) {
            this.registerPerMinute = registerPerMinute;
        }

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getOrderPerMinute() {
            return orderPerMinute;
        }

        public void setOrderPerMinute(int orderPerMinute) {
            this.orderPerMinute = orderPerMinute;
        }
    }

    /** 支付包配置。Mock 是本阶段的默认实现，真实渠道接入时在这里加开关。 */
    public static class Payment {

        private final Mock mock = new Mock();

        public Mock getMock() {
            return mock;
        }

        public static class Mock {

            /** 关掉 Mock 又没有真实适配器时，启动会告警、支付一律拒绝，不静默装成功。 */
            private boolean enabled = true;

            /** 把这些渠道的支付强制置为失败；测试用来覆盖「失败仍待支付」的分支。 */
            private List<String> failChannels = List.of();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<String> getFailChannels() {
                return failChannels;
            }

            public void setFailChannels(List<String> failChannels) {
                this.failChannels = failChannels;
            }
        }
    }

    /** 订单时序配置：支付时限由业务口径定死 24 小时，扫描间隔与批量留给超时任务。 */
    public static class Order {

        private Duration paymentTimeout = Duration.ofHours(24);

        /** 超时扫描间隔（毫秒）；测试里调大，避免定时任务干扰用例。 */
        private long timeoutScanIntervalMs = 60_000;

        /** 每轮最多处理多少笔，避免一次扫全表、长事务。 */
        private int timeoutBatchSize = 200;

        public Duration getPaymentTimeout() {
            return paymentTimeout;
        }

        public void setPaymentTimeout(Duration paymentTimeout) {
            this.paymentTimeout = paymentTimeout;
        }

        public long getTimeoutScanIntervalMs() {
            return timeoutScanIntervalMs;
        }

        public void setTimeoutScanIntervalMs(long timeoutScanIntervalMs) {
            this.timeoutScanIntervalMs = timeoutScanIntervalMs;
        }

        public int getTimeoutBatchSize() {
            return timeoutBatchSize;
        }

        public void setTimeoutBatchSize(int timeoutBatchSize) {
            this.timeoutBatchSize = timeoutBatchSize;
        }
    }

    public static class Pagination {

        private int defaultSize = 10;
        private int maxSize = 100;

        public int getDefaultSize() {
            return defaultSize;
        }

        public void setDefaultSize(int defaultSize) {
            this.defaultSize = defaultSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }
}
