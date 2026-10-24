package com.simpleshop.common.config;

import java.nio.file.Path;
import java.time.Duration;

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
    private final Queue queue = new Queue();
    private final RateLimit rateLimit = new RateLimit();
    private final Pagination pagination = new Pagination();

    public Media getMedia() {
        return media;
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

    public Pagination getPagination() {
        return pagination;
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

    public static class Queue {

        /** 单个商品非终态意向的数量上限。 */
        private int maxSize = 1000;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static class RateLimit {

        private int registerPerMinute = 10;
        private int loginPerMinute = 10;
        private int submitIntentionPerMinute = 10;

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

        public int getSubmitIntentionPerMinute() {
            return submitIntentionPerMinute;
        }

        public void setSubmitIntentionPerMinute(int submitIntentionPerMinute) {
            this.submitIntentionPerMinute = submitIntentionPerMinute;
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
