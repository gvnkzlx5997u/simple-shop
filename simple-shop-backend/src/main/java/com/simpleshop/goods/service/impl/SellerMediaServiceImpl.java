package com.simpleshop.goods.service.impl;

import com.simpleshop.goods.service.SellerMediaService;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.media.MediaPurpose;
import com.simpleshop.common.media.MediaStorageService;
import com.simpleshop.common.media.StoredMedia;

/**
 * 卖家媒体上传。商品发布前先上传，发布时只绑定返回的 URL。
 */
@Service
public class SellerMediaServiceImpl implements SellerMediaService {

    private final MediaStorageService mediaStorage;

    public SellerMediaServiceImpl(MediaStorageService mediaStorage) {
        this.mediaStorage = mediaStorage;
    }

    public StoredMedia upload(MultipartFile file, String purposeValue) {
        MediaPurpose purpose;
        try {
            purpose = MediaPurpose.valueOf(
                    purposeValue == null ? "" : purposeValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.MEDIA_INVALID);
        }
        return mediaStorage.store(file, purpose);
    }
}
