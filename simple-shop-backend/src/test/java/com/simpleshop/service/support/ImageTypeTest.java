package com.simpleshop.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * 图片类型识别（魔数判定）的用例表测试（{@code C-14}、{@code NFR-11}、方案 §6）。
 *
 * <p>用例表：{@code /csv/goods-image-cases.csv}，列
 * {@code caseName, kind, expectedType}。{@code kind} 是「构造什么字节」的记号：
 * <ul>
 *   <li>{@code file:test.jpg} —— 从 classpath 读取<b>真实图片</b>（{@code src/test/resources/images/}）；</li>
 *   <li>其余为测试内构造的字节序列（如 {@code ffd8} 表示只有两个字节 {@code FF D8}）。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么必须包含「魔数截断」类用例</h2>
 * <p>只看前两字节的实现会放过「只有 2 字节的假 JPEG」；PNG 若只比前 4 字节，
 * 就会把第 5–8 字节不符的文件当成 PNG。{@code ffd8}、{@code png-truncated}、
 * {@code png-last-byte-wrong} 三行专门钉住这些边界——它们不会在正常使用中暴露，
 * 但正是「只判扩展名/前缀」这类实现最容易留下的缺口。
 */
class ImageTypeTest {

    /** 真实图片所在目录（classpath）。 */
    private static final String IMAGE_RESOURCE_DIR = "/images/";

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/csv/goods-image-cases.csv", numLinesToSkip = 1)
    @DisplayName("图片魔数识别用例表")
    void imageTypeCases(String caseName, String kind, String expectedType) {
        Optional<ImageType> detected = ImageType.detect(contentOf(kind));

        if ("NONE".equals(expectedType)) {
            assertThat(detected)
                    .as("用例「%s」的 %s 应当无法识别为受支持类型", caseName, kind)
                    .isEmpty();
        } else {
            assertThat(detected)
                    .as("用例「%s」的 %s 应当识别为 %s", caseName, kind, expectedType)
                    .contains(ImageType.valueOf(expectedType));
        }
    }

    @Test
    @DisplayName("真实图片文件：test.jpg → JPEG、test.png → PNG，且扩展名与内容一致")
    void realFixtureImagesAreDetected() {
        assertThat(ImageType.detect(contentOf("file:test.jpg"))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(contentOf("file:test.png"))).contains(ImageType.PNG);

        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.PNG.extension()).isEqualTo("png");
    }

    @Test
    @DisplayName("支持的类型恰为 {jpg, png} 且扩展名均为小写（会直接拼进 pic_url）")
    void supportedExtensionsAreExactlyJpgAndPng() {
        // 契约只允许 JPG/PNG（C-14）。多出一种就说明有人放宽了范围；
        // 出现大写或带点则会让 pic_url 的形状与 §6 的正则不匹配，进而在 I11-06 被自己拒掉。
        assertThat(Arrays.stream(ImageType.values()).map(ImageType::extension))
                .containsExactlyInAnyOrder("jpg", "png");
    }

    @Test
    @DisplayName("内容短于魔数长度时返回空，而不是抛数组越界")
    void shortContentIsHandledWithoutException() {
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    /**
     * 按用例表的 {@code kind} 记号构造字节内容。
     *
     * <p>这里刻意把「构造什么」写成一个 switch 而不是把字节写进 CSV：
     * CSV 里放二进制既不可读也不可靠，而「用哪些字节」本身是<b>代码</b>的关切
     * （用例表关心的是「应当识别成什么」）。
     */
    private static byte[] contentOf(String kind) {
        if (kind == null) {
            return null;
        }
        if (kind.startsWith("file:")) {
            return readClasspathResource(IMAGE_RESOURCE_DIR + kind.substring("file:".length()));
        }
        return switch (kind) {
            // JPEG：FF D8 FF 后跟一个标记段前缀
            case "jpeg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                    0x00, 0x10, 'J', 'F', 'I', 'F', 0x00};
            case "jpeg-garbage" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
                    0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x99};
            // 只有 SOI 两个字节：FF D8 —— 只看前两字节的实现会误判为 JPEG
            case "ffd8" -> new byte[]{(byte) 0xFF, (byte) 0xD8};
            // PNG：完整 8 字节魔数
            case "png" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            case "png-garbage" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x0D};
            case "png-truncated" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A};
            case "png-last-byte-wrong" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0B};
            case "gif" -> "GIF89a".getBytes(StandardCharsets.US_ASCII);
            case "bmp" -> new byte[]{'B', 'M', 0x36, 0x00};
            case "pdf" -> "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
            case "zip" -> new byte[]{0x50, 0x4B, 0x03, 0x04};
            case "html" -> "<html><body>x</body></html>".getBytes(StandardCharsets.UTF_8);
            case "text" -> "just some text, definitely not an image".getBytes(StandardCharsets.UTF_8);
            case "empty" -> new byte[0];
            case "null" -> null;
            default -> throw new IllegalArgumentException("用例表的 kind 无法构造内容: " + kind);
        };
    }

    private static byte[] readClasspathResource(String path) {
        try (InputStream in = ImageTypeTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("测试资源不存在: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取测试资源失败: " + path, e);
        }
    }
}
