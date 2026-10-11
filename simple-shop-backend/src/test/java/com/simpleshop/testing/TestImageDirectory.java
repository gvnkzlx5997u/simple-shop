package com.simpleshop.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import com.simpleshop.config.AppProperties;

/**
 * 测试期图片目录的清理（供会真的写文件的用例共用，如 {@code I11-05} 的上传用例）。
 *
 * <h2>⚠️ 删除前必须确认目标路径（fail closed）</h2>
 * <p>这个目录来自配置，而生产配置与测试配置只差一个键值
 * （{@code simple-shop.image.directory}：{@code ./data/images} vs {@code ./target/test-images}）。
 * 万一 {@code src/test/resources/application-test.yml} 的覆盖被人删掉，
 * 这里的删除动作就会指向 {@code ./data/images}——即<b>真实的商品图片</b>，
 * 而 {@code DEC-09} 已定「图片只存文件系统、归档也不搬迁」，删掉就是<b>不可恢复的数据丢失</b>。
 *
 * <p>因此本方法<b>先断言</b>路径确实以 {@code test-images} 结尾，不满足就让用例失败，
 * 绝不「尽力删一删」。把这条护栏收在一个类里，是为了避免每个用到上传的测试各写一份、
 * 而其中某一份忘了加上判断。
 *
 * <h2>为什么不改成「记录创建了哪些文件再逐个删」</h2>
 * <p>那样更安全，但会污染每个用例（要一路传递返回的路径）。而测试目录本身已被
 * {@code mvn clean} 覆盖，整目录删除既简单又彻底。用路径断言换取这份简单，是划算的。
 */
public final class TestImageDirectory {

    /** 测试专用目录名。生产目录是 {@code data/images}，与之明确区分。 */
    private static final String TEST_DIRECTORY_SUFFIX = "test-images";

    private TestImageDirectory() {
    }

    /**
     * 递归删除配置的测试图片目录。
     *
     * @param properties 应用配置（取 {@code simple-shop.image.directory}）
     * @throws AssertionError 若解析出的路径不是测试专用目录（见类注释）
     */
    public static void delete(AppProperties properties) {
        Path directory = properties.getImage().directoryPath();
        assertThat(directory.toString())
                .as("测试只允许操作 %s 目录，实际为 %s —— 若该断言失败，"
                                + "说明 application-test.yml 里 simple-shop.image.directory 的覆盖被移除了",
                        TEST_DIRECTORY_SUFFIX, directory)
                .endsWith(TEST_DIRECTORY_SUFFIX);

        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("清理测试图片目录失败: " + path, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("遍历测试图片目录失败: " + directory, e);
        }
    }
}
