package com.simpleshop.aftersale.controller.buyer;

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

import com.simpleshop.aftersale.service.AftersaleImageUploadService;
import com.simpleshop.common.api.ApiResponse;
import com.simpleshop.common.media.StoredMedia;
import com.simpleshop.goods.dto.MediaUploadData;

/**
 * 买家媒体上传：只开放售后凭证图。
 *
 * <p>路径是通用的 {@code /api/buyer/media}，因为上传发生在建售后单之前，此时还没有售后单 ID；
 * 用途白名单把能力收窄到「售后图片」，其他用途一律 20027。
 */
@RestController
@RequestMapping("/api/buyer/media")
@Tag(name = "买家媒体")
@SecurityRequirement(name = "bearerAuth")
public class BuyerMediaController {

    private final AftersaleImageUploadService uploads;

    public BuyerMediaController(AftersaleImageUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传售后凭证图；purpose 只接受 AFTERSALE_IMAGE")
    public ApiResponse<MediaUploadData> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose) {
        StoredMedia stored = uploads.upload(file, purpose);
        return ApiResponse.ok(new MediaUploadData(
                stored.url(), stored.kind().name(), stored.sizeBytes()));
    }
}
