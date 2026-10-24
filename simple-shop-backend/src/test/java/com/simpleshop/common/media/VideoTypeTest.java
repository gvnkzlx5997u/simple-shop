package com.simpleshop.common.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VideoTypeTest {

    @Test
    @DisplayName("偏移 4 处为 ftyp 的 MP4 被识别")
    void detectsMp4FtypBox() {
        assertThat(VideoType.detect(mp4Header())).contains(VideoType.MP4);
        assertThat(VideoType.MP4.extension()).isEqualTo("mp4");
    }

    @Test
    @DisplayName("过短、缺少 ftyp、普通文本都不识别")
    void rejectsNonMp4Content() {
        assertThat(VideoType.detect(null)).isEmpty();
        assertThat(VideoType.detect(new byte[0])).isEmpty();
        assertThat(VideoType.detect(new byte[]{0, 0, 0, 0, 'f', 't', 'y'})).isEmpty();
        assertThat(VideoType.detect("not a video".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("ftyp 不在偏移 4 时不识别")
    void requiresFtypAtOffsetFour() {
        byte[] shifted = new byte[]{'f', 't', 'y', 'p', 0, 0, 0, 0, 'i', 's', 'o', 'm'};
        assertThat(VideoType.detect(shifted)).isEmpty();
    }

    @Test
    @DisplayName("同样使用 ftyp 的 HEIC 图片与 MOV 视频不识别")
    void rejectsOtherFtypContainers() {
        assertThat(VideoType.detect(ftypWithBrand("heic"))).isEmpty();
        assertThat(VideoType.detect(ftypWithBrand("mif1"))).isEmpty();
        assertThat(VideoType.detect(ftypWithBrand("qt  "))).isEmpty();
        assertThat(VideoType.detect(ftypWithBrand("isom"))).contains(VideoType.MP4);
    }

    static byte[] mp4Header() {
        return ftypWithBrand("isom");
    }

    private static byte[] ftypWithBrand(String brand) {
        byte[] header = new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 0, 0, 0, 0};
        byte[] brandBytes = brand.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(brandBytes, 0, header, 8, 4);
        return header;
    }
}