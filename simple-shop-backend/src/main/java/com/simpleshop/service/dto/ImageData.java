package com.simpleshop.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 图片上传的响应数据（{@code I11-05} 的 {@code data}）：{@code {pic_url}}。
 *
 * <p>依据：方案 §4.6.2、§6。
 *
 * <p>返回值<b>含</b>配置的 URL 前缀（默认 {@code /images}），形如
 * {@code /images/2026/10/<uuid>.jpg}，前端 {@code <img src>} 可直接使用
 * （已定 Q-5/Q-6）。前端拿到后把它原样回填到 {@code I11-06} 的 {@code pic_url}。
 *
 * <p><b>为什么只返回一个字段而不是整个商品对象</b>：{@code I11-05} 发生在
 * 「填写发布表单」阶段——此刻商品还不存在，没有 {@code id}/{@code status} 可言。
 * 上传与发布是<b>两个</b>动作（契约也分成两个接口），上传只是把文件先放好。
 *
 * @param picUrl 相对路径，含 URL 前缀
 */
public record ImageData(@JsonProperty("pic_url") String picUrl) {

    /** 由 {@code ImageStorageService} 生成的路径构造。 */
    public static ImageData of(String picUrl) {
        return new ImageData(picUrl);
    }
}
