package com.simpleshop.goods.controller.seller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.media.StoredMedia;
import com.simpleshop.goods.dto.MediaUploadData;
import com.simpleshop.goods.service.SellerMediaService;

/**
 * 卖家媒体上传。商品主图、图集和描述媒体都通过这里先上传。
 */
@RestController
@RequestMapping("/api/seller/media")
@Tag(name = "卖家媒体")
@SecurityRequirement(name = "bearerAuth")
public class SellerMediaController {

    private final SellerMediaService mediaService;

    public SellerMediaController(SellerMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传媒体；purpose 取 GOODS_IMAGE 或 DESCRIPTION_MEDIA")
    public ApiResponse<MediaUploadData> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {
        StoredMedia stored = mediaService.upload(file, purpose);
        return ApiResponse.ok(new MediaUploadData(
                stored.url(), stored.kind().name(), stored.sizeBytes()));
    }
}
