package com.simpleshop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 工程配置的绑定与行为验证（S1 工程改造的可执行凭据）。
 *
 * <p>本用例是<b>长期保留的回归护栏</b>，不是一次性的临时校验。它挡住三类「静默失效」：
 * <ol>
 *   <li>{@link AppProperties} 的绑定——{@code @ConfigurationProperties} 若未注册成 Bean，
 *       不会报错，只会取到字段默认值或注入失败；断言具体取值才能发现「写了配置但没生效」。</li>
 *   <li>multipart 上限——{@code 5MB}/{@code 6MB} 是 {@code C-14} 的落点，
 *       被改大就等于放开了图片尺寸约束（而 Service 侧的独立校验是第二道防线，不该被依赖）。</li>
 *   <li>Jackson 的 {@code FAIL_ON_UNKNOWN_PROPERTIES}——见 {@link JacksonConfig} 的类注释：
 *       一旦被关掉，请求体里的字段拼写错误会被<b>静默忽略</b>并落成 {@code null}，
 *       前端拿不到任何线索。</li>
 * </ol>
 *
 * <p>用 {@code @SpringBootTest} 不指定 {@code webEnvironment}：默认 MOCK，
 * 不启动真实 servlet 容器，但加载完整上下文（含数据源与 Flyway），
 * 因此本用例同时证明「加了 web 与 validation 两个 starter 之后，上下文仍能正常装配」。
 */
@SpringBootTest
class AppPropertiesTest {

    @Autowired
    private AppProperties props;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxRequestSize;

    @Test
    @DisplayName("simple-shop.* 业务配置绑定到 AppProperties")
    void bindsAllProperties() {
        // 图片（Q-5/Q-6：默认 ./data/images，前缀 /images）
        assertThat(props.getImage().getDirectory()).isEqualTo("./data/images");
        assertThat(props.getImage().getUrlPrefix()).isEqualTo("/images");
        // directoryPath() 必须是绝对且已归一化的路径——Service 用它做「路径穿越」防护，
        // 因此这里连带断言其语义（绝对 + 归一化后不变）。
        assertThat(props.getImage().directoryPath().isAbsolute()).isTrue();
        assertThat(props.getImage().directoryPath().toString())
                .isEqualTo(props.getImage().directoryPath().normalize().toString());

        // 会话（C-07 / DEC-13 的 30 分钟；12-P7 的宽松窗口 ±1 分钟）
        // 注：会话清理任务的间隔刻意不做成配置项（@Scheduled 的 fixedDelay 要求编译期常量），
        // 固定 60 秒，见 SessionStore.PURGE_INTERVAL_MS 的注释。
        assertThat(props.getSession().getTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(props.getSession().getTimeoutTolerance()).isEqualTo(Duration.ofSeconds(60));

        // 队列上限（DEC-23 / NFR-07）
        assertThat(props.getQueue().getMaxSize()).isEqualTo(1000);

        // 提交意向限流（NFR-17 / 12-P6）
        assertThat(props.getRateLimit().getSubmitIntentionPerMinute()).isEqualTo(10);
    }

    @Test
    @DisplayName("图片上传上限为 5MB / 6MB（C-14、NFR-11）")
    void bindsMultipartLimits() {
        // Spring 会把 "5MB" 解析为 DataSize；这里直接断言配置字面量，
        // 避免因单位换算写法不同而产生无意义的脆弱断言。
        assertThat(maxFileSize).isEqualToIgnoringCase("5MB");
        assertThat(maxRequestSize).isEqualToIgnoringCase("6MB");
    }

    @Test
    @DisplayName("Jackson：契约未定义的请求字段必须报错，不静默忽略")
    void rejectsUnknownProperties() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"pic_ur1\":\"/images/a.jpg\"}", SampleDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageContaining("pic_ur1");
    }

    @Test
    @DisplayName("Jackson：null 字段必须保留键（契约 11.3.1「不省略键」）")
    void keepsNullFields() throws Exception {
        String json = objectMapper.writeValueAsString(new SampleDto());
        // 若有人加了 @JsonInclude(NON_NULL) 或配置了 default-property-inclusion，
        // 这里的键会消失——本断言就是方案 §8.3 第 2 条 JSON 断言的最小版本。
        assertThat(json).contains("\"pic_url\":null");
        assertThat(json).contains("\"freeze_by\":null");
    }

    @Test
    @DisplayName("Jackson：时间不输出纪元时间戳（DTO 一律用 String，此处防全局开关被打开）")
    void doesNotWriteDatesAsTimestamps() throws Exception {
        String json = objectMapper.writeValueAsString(new TimeDto());
        // ISO-8601 字符串形态
        assertThat(json).contains("2026-10-05T12:34:56");
        // 纪元时间戳形态（纯数字数组）不应出现：若 WRITE_DATES_AS_TIMESTAMPS 被打开，
        // 输出会变成 {"createAt":[2026,10,5,12,34,56]} 这种数组。
        assertThat(json).doesNotContain("[2026,");
    }

    @Test
    @DisplayName("DTO 的 UTC → 带 Z 的 ISO 8601 转换式（方案 §4.6.4 的标准写法）")
    void utcStringConversionKeepsZuluSuffix() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 5, 12, 34, 56);
        String iso = utc.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertThat(iso).isEqualTo("2026-10-05T12:34:56Z");
    }

    /** 仅用于 Jackson 断言的样例，字段名刻意用 snake_case 以贴近真实 DTO 的写法。 */
    static class SampleDto {
        @JsonProperty("pic_url")
        public String picUrl;

        @JsonProperty("freeze_by")
        public String freezeBy;
    }

    /** 仅用于验证时间序列化开关。 */
    static class TimeDto {
        public LocalDateTime createAt = LocalDateTime.of(2026, 10, 5, 12, 34, 56);
    }
}
