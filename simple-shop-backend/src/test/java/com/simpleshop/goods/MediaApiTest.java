package com.simpleshop.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import com.simpleshop.testing.ApiTestBase;

class MediaApiTest extends ApiTestBase {

    @Test
    @DisplayName("卖家可以上传商品图片")
    void sellerCanUploadGoodsImage() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = upload(token, "GOODS_IMAGE", jpeg());

        assertApiCode(result, 0);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("url").asText()).startsWith("/media/goods/");
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("kind").asText()).isEqualTo("IMAGE");
    }

    @Test
    @DisplayName("卖家可以上传描述视频")
    void sellerCanUploadDescriptionVideo() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = upload(token, "DESCRIPTION_MEDIA", mp4());

        assertApiCode(result, 0);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("url").asText()).startsWith("/media/description/");
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("kind").asText()).isEqualTo("VIDEO");
    }

    @Test
    @DisplayName("商品图片用途拒绝视频")
    void goodsImagePurposeRejectsVideo() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = upload(token, "GOODS_IMAGE", mp4());

        assertApiCode(result, 20027);
    }

    @Test
    @DisplayName("非法 purpose 返回媒体错误")
    void invalidPurposeIsRejected() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = upload(token, "UNKNOWN", jpeg());

        assertApiCode(result, 20026);
    }

    @Test
    @DisplayName("未登录或买家令牌不能上传卖家媒体")
    void uploadRequiresSellerRole() throws Exception {
        MvcResult anonymous = mockMvc.perform(multipart("/api/seller/media")
                        .file(jpeg())
                        .param("purpose", "GOODS_IMAGE"))
                .andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        String buyerUsername = unique("buyer-");
        registerBuyer(buyerUsername, "Buyer@1234", "13800000000", null);
        String buyerToken = loginBuyer(buyerUsername, "Buyer@1234");
        MvcResult buyer = upload(buyerToken, "GOODS_IMAGE", jpeg());
        assertApiStatusAndCode(buyer, 401, 10002);
    }

    @Test
    @DisplayName("缺少文件或 purpose 时返回参数错误")
    void uploadRequiresFileAndPurpose() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult noFile = mockMvc.perform(multipart("/api/seller/media")
                        .param("purpose", "GOODS_IMAGE")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiStatusAndCode(noFile, 400, 50002);

        MvcResult noPurpose = mockMvc.perform(multipart("/api/seller/media")
                        .file(jpeg())
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiStatusAndCode(noPurpose, 400, 50002);
    }

    @Test
    @DisplayName("上传接口消费 multipart 并返回大小")
    void uploadReturnsSize() throws Exception {
        String token = loginSeller("Abcd@1234");

        MvcResult result = upload(token, "DESCRIPTION_MEDIA", jpeg());

        assertApiCode(result, 0);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("size_bytes").asLong()).isEqualTo(12);
    }

    private MvcResult upload(String token, String purpose, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/seller/media")
                        .file(file)
                        .param("purpose", purpose)
                        .header("Authorization", bearer(token)))
                .andReturn();
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                        0, 0, 0, 0, 0, 0, 0, 0});
    }

    private static MockMultipartFile mp4() {
        return new MockMultipartFile("file", "a.mp4", "video/mp4",
                new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
    }
}
