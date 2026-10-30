package com.simpleshop.goods.service;

import org.springframework.web.multipart.MultipartFile;
import com.simpleshop.common.media.StoredMedia;

/**
 * 卖家媒体上传。
 */
public interface SellerMediaService {

    StoredMedia upload(MultipartFile file, String purposeValue);
}
