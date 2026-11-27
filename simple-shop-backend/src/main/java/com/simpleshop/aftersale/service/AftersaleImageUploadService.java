package com.simpleshop.aftersale.service;

import org.springframework.stereotype.Service;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.media.MediaPurpose;
import com.simpleshop.common.media.MediaStorageService;
import com.simpleshop.common.media.StoredMedia;

import org.springframework.web.multipart.MultipartFile;

/**
 * 买家图片上传：目前只有售后凭证图一种用途。
 *
 * <p>为什么买家上传要单独一个入口而不是复用卖家那个：卖家入口允许商品图与描述媒体，
 * 买家传商品图没有业务含义。这里把用途白名单收窄成 {@code AFTERSALE_IMAGE}，
 * 其他用途一律 20027——不靠前端自觉。
 */
@Service
public class AftersaleImageUploadService {

    private final MediaStorageService media;

    public AftersaleImageUploadService(MediaStorageService media) {
        this.media = media;
    }

    public StoredMedia upload(MultipartFile file, String purpose) {
        if (!MediaPurpose.AFTERSALE_IMAGE.name().equalsIgnoreCase(trim(purpose))) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_ALLOWED,
                    "buyers can only upload AFTERSALE_IMAGE");
        }
        return media.store(file, MediaPurpose.AFTERSALE_IMAGE);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
