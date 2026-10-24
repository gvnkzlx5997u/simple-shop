package com.simpleshop.common.media;

/**
 * 已落盘媒体的描述。
 *
 * @param url       对外访问的相对 URL
 * @param kind      媒体类型
 * @param sizeBytes 文件字节数
 */
public record StoredMedia(String url, MediaKind kind, long sizeBytes) {
}
