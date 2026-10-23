package com.simpleshop.common.media;

import java.util.Optional;

/**
 * 允许上传的图片类型。按文件开头的魔数识别，不信任扩展名与 Content-Type。
 */
public enum ImageType {

    JPEG("jpg", 0xFF, 0xD8, 0xFF),
    PNG("png", 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

    private final String extension;
    private final byte[] magic;

    ImageType(String extension, int... magic) {
        this.extension = extension;
        this.magic = new byte[magic.length];
        for (int i = 0; i < magic.length; i++) {
            this.magic[i] = (byte) magic[i];
        }
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        for (ImageType type : values()) {
            if (type.matches(content)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private boolean matches(byte[] content) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
