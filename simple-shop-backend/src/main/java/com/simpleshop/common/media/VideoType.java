package com.simpleshop.common.media;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * 允许上传的视频类型。当前只接受 MP4 容器。
 *
 * <p>ISO Base Media 文件在偏移 4 处有 {@code ftyp} 标记，并在偏移 8 处写主品牌。
 * 这里同时校验 {@code ftyp} 与常见 MP4 品牌，避免把同样使用 {@code ftyp} 的
 * HEIC 图片或 MOV 视频当成 MP4。不信任扩展名与 Content-Type。
 */
public enum VideoType {

    MP4("mp4");

    private static final int FTYP_OFFSET = 4;
    private static final int BRAND_OFFSET = 8;

    /** 常见 MP4 主品牌；MOV 的 qt 与 HEIC 的 heic/mif1 不在其中。 */
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6",
            "mp41", "mp42", "mp71",
            "avc1", "M4V ", "M4VH", "M4VP",
            "dash", "mmp4", "MSNV",
            "3gp4", "3gp5", "3gp6", "F4V ");

    private final String extension;

    VideoType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static Optional<VideoType> detect(byte[] content) {
        if (content == null || content.length < BRAND_OFFSET + 4) {
            return Optional.empty();
        }
        boolean ftyp = content[FTYP_OFFSET] == 'f'
                && content[FTYP_OFFSET + 1] == 't'
                && content[FTYP_OFFSET + 2] == 'y'
                && content[FTYP_OFFSET + 3] == 'p';
        if (!ftyp) {
            return Optional.empty();
        }
        String brand = new String(content, BRAND_OFFSET, 4, StandardCharsets.US_ASCII);
        return MP4_BRANDS.contains(brand) ? Optional.of(MP4) : Optional.empty();
    }
}
