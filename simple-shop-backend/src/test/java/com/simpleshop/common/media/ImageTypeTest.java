package com.simpleshop.common.media;

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
 * 图片魔数识别用例表测试。
 *
 * <p>包含魔数截断、错误前缀等边界，防止只看扩展名或前缀的实现蒙混过关。
 */
class ImageTypeTest {

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
    @DisplayName("真实图片文件：test.jpg 为 JPEG，test.png 为 PNG")
    void realFixtureImagesAreDetected() {
        assertThat(ImageType.detect(contentOf("file:test.jpg"))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(contentOf("file:test.png"))).contains(ImageType.PNG);

        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.PNG.extension()).isEqualTo("png");
    }

    @Test
    @DisplayName("支持的类型恰为 jpg 与 png")
    void supportedExtensionsAreExactlyJpgAndPng() {
        assertThat(Arrays.stream(ImageType.values()).map(ImageType::extension))
                .containsExactlyInAnyOrder("jpg", "png");
    }

    @Test
    @DisplayName("内容过短或为空时返回空，不抛异常")
    void shortContentIsHandledWithoutException() {
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }

    private static byte[] contentOf(String kind) {
        if (kind == null) {
            return null;
        }
        if (kind.startsWith("file:")) {
            return readClasspathResource(IMAGE_RESOURCE_DIR + kind.substring("file:".length()));
        }
        return switch (kind) {
            case "jpeg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                    0x00, 0x10, 'J', 'F', 'I', 'F', 0x00};
            case "jpeg-garbage" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
                    0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x99};
            case "ffd8" -> new byte[]{(byte) 0xFF, (byte) 0xD8};
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
            default -> throw new IllegalArgumentException("unknown kind: " + kind);
        };
    }

    private static byte[] readClasspathResource(String path) {
        try (InputStream in = ImageTypeTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + path);
            }
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read test resource: " + path, exception);
        }
    }
}
