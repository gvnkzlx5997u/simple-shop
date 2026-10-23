package com.simpleshop.common.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务配置，前缀 {@code simple-shop}。
 *
 * <p>所有值都可以用环境变量覆盖，便于容器部署。
 */
@Component
@ConfigurationProperties(prefix = "simple-shop")
public class AppProperties {

    private final Image image = new Image();
    private final Session session = new Session();
    private final Queue queue = new Queue();
    private final RateLimit rateLimit = new RateLimit();
    private final Pagination pagination = new Pagination();

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

    public Pagination getPagination() {
        return pagination;
    }

    public static class Image {

        private String directory = "./data/images";
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

        /** 归一化后的图片目录绝对路径，用于目录穿越校验。 */
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
