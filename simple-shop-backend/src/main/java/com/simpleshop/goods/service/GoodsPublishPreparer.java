package com.simpleshop.goods.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.media.MediaPurpose;
import com.simpleshop.common.media.MediaStorageService;
import com.simpleshop.common.text.TextRules;
import com.simpleshop.goods.dto.DescriptionBlockRequest;
import com.simpleshop.goods.enums.ContentBlockType;
import com.simpleshop.goods.support.GoodsRules;

/**
 * 发布商品前的图片与描述校验。媒体 URL 必须指向本服务已落盘的文件。
 */
@Component
public class GoodsPublishPreparer {

    private static final int MAX_GALLERY_IMAGES = 8;
    private static final int MAX_TOTAL_IMAGES = 9;

    private final MediaStorageService media;

    public GoodsPublishPreparer(MediaStorageService media) {
        this.media = media;
    }

    public Prepared prepare(
            String mainImage,
            List<String> galleryImages,
            List<DescriptionBlockRequest> description) {
        return new Prepared(
                prepareImages(mainImage, galleryImages),
                prepareDescription(description));
    }

    private List<ImageInput> prepareImages(String mainImage, List<String> galleryImages) {
        List<String> gallery = galleryImages == null ? List.of() : galleryImages;
        if (mainImage == null && !gallery.isEmpty()) {
            throw new BusinessException(ErrorCode.MAIN_IMAGE_REQUIRED);
        }
        if (gallery.size() > MAX_GALLERY_IMAGES) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        List<ImageInput> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (mainImage != null) {
            addImage(result, seen, mainImage, true);
        }
        for (String url : gallery) {
            addImage(result, seen, url, false);
        }
        if (result.size() > MAX_TOTAL_IMAGES) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }
        return result;
    }

    private void addImage(List<ImageInput> result, Set<String> seen, String url, boolean main) {
        if (url == null || url.isBlank() || !seen.add(url)) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID);
        }
        media.verifyStoredUrl(url, MediaPurpose.GOODS_IMAGE);
        result.add(new ImageInput(url, main));
    }

    private List<BlockInput> prepareDescription(List<DescriptionBlockRequest> description) {
        if (description == null || description.isEmpty()) {
            return List.of();
        }
        List<BlockInput> result = new ArrayList<>(description.size());
        int totalTextLength = 0;
        for (DescriptionBlockRequest block : description) {
            if (block == null) {
                throw descriptionInvalid();
            }
            String type = block.type() == null
                    ? ""
                    : block.type().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case "TEXT" -> {
                    if (block.url() != null) {
                        throw descriptionInvalid();
                    }
                    String text = GoodsRules.descriptionText(block.text());
                    totalTextLength += TextRules.charCount(text);
                    if (totalTextLength > GoodsRules.MAX_DESCRIPTION_TEXT_LENGTH) {
                        throw descriptionInvalid();
                    }
                    result.add(new BlockInput(ContentBlockType.TEXT, text, null));
                }
                case "IMAGE", "VIDEO" -> {
                    if (block.text() != null || block.url() == null || block.url().isBlank()) {
                        throw descriptionInvalid();
                    }
                    media.verifyStoredUrl(block.url(), MediaPurpose.DESCRIPTION_MEDIA);
                    boolean video = block.url().toLowerCase(Locale.ROOT).endsWith(".mp4");
                    if (type.equals("VIDEO") != video) {
                        throw descriptionInvalid();
                    }
                    result.add(new BlockInput(ContentBlockType.valueOf(type), null, block.url()));
                }
                default -> throw descriptionInvalid();
            }
        }
        return result;
    }

    private static BusinessException descriptionInvalid() {
        return new BusinessException(ErrorCode.GOODS_DESCRIPTION_INVALID);
    }

    public record Prepared(List<ImageInput> images, List<BlockInput> blocks) {

        public Prepared {
            images = images == null ? List.of() : List.copyOf(images);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    public record ImageInput(String url, boolean main) {
    }

    public record BlockInput(ContentBlockType type, String text, String url) {
    }
}
