package com.simpleshop.common.media;

import java.util.Set;

/**
 * 上传用途。用途决定允许的媒体类型和大小限制：
 * 商品主图/图集只允许图片；描述内容块允许图片和视频。
 */
public enum MediaPurpose {

    GOODS_IMAGE("goods", Set.of(MediaKind.IMAGE)),
    DESCRIPTION_MEDIA("description", Set.of(MediaKind.IMAGE, MediaKind.VIDEO)),
    /** 买家申请售后时上传的凭证图，只允许图片。 */
    AFTERSALE_IMAGE("aftersale", Set.of(MediaKind.IMAGE));

    private final String folder;
    private final Set<MediaKind> allowedKinds;

    MediaPurpose(String folder, Set<MediaKind> allowedKinds) {
        this.folder = folder;
        this.allowedKinds = allowedKinds;
    }

    public String folder() {
        return folder;
    }

    public boolean allows(MediaKind kind) {
        return allowedKinds.contains(kind);
    }
}
