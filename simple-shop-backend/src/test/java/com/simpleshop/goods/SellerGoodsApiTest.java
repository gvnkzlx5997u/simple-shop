package com.simpleshop.goods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.simpleshop.account.entity.BuyerAccount;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.goods.entity.Goods;
import com.simpleshop.goods.enums.GoodsStatus;
import com.simpleshop.intention.entity.Intention;
import com.simpleshop.testing.ApiTestBase;

class SellerGoodsApiTest extends ApiTestBase {

    private static final String SELLER_PASSWORD = "Abcd@1234";

    @Test
    @DisplayName("卖家可以发布带主图、图集和图文视频描述的商品")
    void publishesGoodsWithImagesAndDescription() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        String name = unique("商品-");
        String mainImage = upload(token, "GOODS_IMAGE", jpeg());
        String galleryImage = upload(token, "GOODS_IMAGE", jpeg());
        String descriptionImage = upload(token, "DESCRIPTION_MEDIA", jpeg());
        String descriptionVideo = upload(token, "DESCRIPTION_MEDIA", mp4());

        Map<String, Object> body = goodsBody(name, "99.99", 3, category.getId());
        body.put("main_image", mainImage);
        body.put("gallery_images", List.of(galleryImage));
        body.put("description", List.of(
                Map.of("type", "TEXT", "text", "第一段"),
                Map.of("type", "IMAGE", "url", descriptionImage),
                Map.of("type", "VIDEO", "url", descriptionVideo),
                Map.of("type", "TEXT", "text", "第二段")));

        MvcResult published = publish(token, body);
        assertApiCode(published, 0);
        JsonNode data = data(published);
        String id = data.path("id").asText();

        assertThat(id).startsWith("G");
        assertThat(data.path("name").asText()).isEqualTo(name);
        assertThat(data.path("price").decimalValue()).isEqualByComparingTo("99.99");
        assertThat(data.path("stock").asInt()).isEqualTo(3);
        assertThat(data.path("status").asText()).isEqualTo("ON_SALE");
        assertThat(data.path("category").path("l1_id").asText())
                .isEqualTo(category.getL1().getId());
        assertThat(data.path("category").path("l2_id").asText()).isEqualTo(category.getId());
        assertThat(data.path("main_image_url").asText()).isEqualTo(mainImage);
        assertThat(data.path("purchasable").asBoolean()).isTrue();
        assertThat(data.path("create_at").isNull()).isFalse();
        assertThat(data.path("update_at").isNull()).isFalse();
        assertThat(queueSequences.findWithLockByGoodsId(id)).isPresent();
        assertThat(queueSequences.findWithLockByGoodsId(id).orElseThrow().getCurrentValue()).isZero();

        assertThat(data.path("images").size()).isEqualTo(2);
        assertThat(data.path("images").get(0).path("is_main").asBoolean()).isTrue();
        assertThat(data.path("images").get(0).path("sort_no").asInt()).isEqualTo(1);
        assertThat(data.path("images").get(1).path("sort_no").asInt()).isEqualTo(2);

        assertThat(data.path("description").size()).isEqualTo(4);
        assertThat(data.path("description").get(0).path("type").asText()).isEqualTo("TEXT");
        assertThat(data.path("description").get(0).path("text").asText()).isEqualTo("第一段");
        assertThat(data.path("description").get(1).path("url").asText()).isEqualTo(descriptionImage);
        assertThat(data.path("description").get(2).path("type").asText()).isEqualTo("VIDEO");
        assertThat(data.path("description").get(3).path("text").asText()).isEqualTo("第二段");

        MvcResult sellerDetail = mockMvc.perform(get("/api/seller/goods/{id}", id)
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(sellerDetail, 0);
        assertThat(data(sellerDetail).path("id").asText()).isEqualTo(id);

        MvcResult publicDetail = mockMvc.perform(get("/api/public/goods/{id}", id)).andReturn();
        assertApiCode(publicDetail, 0);
        assertThat(data(publicDetail).path("main_image_url").asText()).isEqualTo(mainImage);

        MvcResult publicList = mockMvc.perform(get("/api/public/goods").param("keyword", name))
                .andReturn();
        assertApiCode(publicList, 0);
        assertThat(data(publicList).path("total").asLong()).isEqualTo(1);
        assertThat(data(publicList).path("items").get(0).path("id").asText()).isEqualTo(id);
        assertThat(data(publicList).path("items").get(0).path("main_image_url").asText())
                .isEqualTo(mainImage);
    }

    @Test
    @DisplayName("发布商品时校验名称、价格、库存与类别")
    void rejectsInvalidPublishFields() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();

        Map<String, Object> blankName = goodsBody("  ", "99.99", 1, category.getId());
        assertApiCode(publish(token, blankName), 20012);

        Map<String, Object> zeroPrice = goodsBody(unique("商品-"), "0", 1, category.getId());
        assertApiCode(publish(token, zeroPrice), 20014);

        Map<String, Object> zeroStock = goodsBody(unique("商品-"), "99.99", 0, category.getId());
        assertApiCode(publish(token, zeroStock), 20015);

        Map<String, Object> l1Category = goodsBody(
                unique("商品-"), "99.99", 1, category.getL1().getId());
        assertApiCode(publish(token, l1Category), 20002);

        Map<String, Object> unknownCategory = goodsBody(
                unique("商品-"), "99.99", 1, "C2-not-exist");
        assertApiStatusAndCode(publish(token, unknownCategory), 404, 20001);
    }

    @Test
    @DisplayName("主图、图集规则：无主图禁止图集，图集最多 8 张，URL 不得重复")
    void enforcesImageRules() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();

        String galleryOnly = upload(token, "GOODS_IMAGE", jpeg());
        Map<String, Object> noMain = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        noMain.put("gallery_images", List.of(galleryOnly));
        assertApiCode(publish(token, noMain), 20018);

        String main = upload(token, "GOODS_IMAGE", jpeg());
        Map<String, Object> duplicate = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        duplicate.put("main_image", main);
        duplicate.put("gallery_images", List.of(main));
        assertApiCode(publish(token, duplicate), 20016);

        Map<String, Object> external = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        external.put("main_image", "https://example.com/goods.jpg");
        assertApiCode(publish(token, external), 20016);

        String descriptionImage = upload(token, "DESCRIPTION_MEDIA", jpeg());
        Map<String, Object> wrongPurpose = goodsBody(
                unique("商品-"), "99.99", 1, category.getId());
        wrongPurpose.put("main_image", descriptionImage);
        assertApiCode(publish(token, wrongPurpose), 20016);

        List<String> nineGallery = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            nineGallery.add(upload(token, "GOODS_IMAGE", jpeg()));
        }
        Map<String, Object> tooMany = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        tooMany.put("main_image", main);
        tooMany.put("gallery_images", nineGallery);
        assertApiCode(publish(token, tooMany), 20017);
    }

    @Test
    @DisplayName("描述规则：文本总长 1000、拒绝空文本和错误媒体类型")
    void enforcesDescriptionRules() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();

        Map<String, Object> tooLong = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        tooLong.put("description", List.of(Map.of("type", "TEXT", "text", "字".repeat(1001))));
        assertApiCode(publish(token, tooLong), 20013);

        Map<String, Object> totalTooLong = goodsBody(
                unique("商品-"), "99.99", 1, category.getId());
        totalTooLong.put("description", List.of(
                Map.of("type", "TEXT", "text", "字".repeat(501)),
                Map.of("type", "TEXT", "text", "字".repeat(500))));
        assertApiCode(publish(token, totalTooLong), 20013);

        Map<String, Object> exactlyOneThousand = goodsBody(
                unique("商品-"), "99.99", 1, category.getId());
        exactlyOneThousand.put("description", List.of(
                Map.of("type", "TEXT", "text", "字".repeat(500)),
                Map.of("type", "TEXT", "text", "字".repeat(500))));
        assertApiCode(publish(token, exactlyOneThousand), 0);

        Map<String, Object> blankText = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        blankText.put("description", List.of(Map.of("type", "TEXT", "text", "  ")));
        assertApiCode(publish(token, blankText), 20013);

        Map<String, Object> missingMedia = goodsBody(unique("商品-"), "99.99", 1, category.getId());
        missingMedia.put("description", List.of(
                Map.of("type", "IMAGE", "url", "/media/description/2026/10/not-found.jpg")));
        assertApiCode(publish(token, missingMedia), 20026);

        String video = upload(token, "DESCRIPTION_MEDIA", mp4());
        Map<String, Object> videoAsImage = goodsBody(
                unique("商品-"), "99.99", 1, category.getId());
        videoAsImage.put("description", List.of(Map.of("type", "IMAGE", "url", video)));
        assertApiCode(publish(token, videoAsImage), 20013);
    }

    @Test
    @DisplayName("无图片、无描述的商品可以发布")
    void publishesGoodsWithoutMedia() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        String name = unique("素商品-");

        MvcResult result = publish(token, goodsBody(name, "10", 1, category.getId()));

        assertApiCode(result, 0);
        JsonNode data = data(result);
        assertThat(data.path("images").size()).isZero();
        assertThat(data.path("description").size()).isZero();
        assertThat(data.path("main_image_url").isNull()).isTrue();
        assertThat(data.path("purchasable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("卖家列表按状态、一级/二级类别筛选")
    void sellerListFiltersByStatusAndCategory() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 categoryA = createCategory();
        CategoryL2 categoryB = createCategory();

        String a1 = data(publish(token, goodsBody(unique("A1-"), "10", 1, categoryA.getId())))
                .path("id").asText();
        String a2 = data(publish(token, goodsBody(unique("A2-"), "10", 1, categoryA.getId())))
                .path("id").asText();
        String b1 = data(publish(token, goodsBody(unique("B1-"), "10", 1, categoryB.getId())))
                .path("id").asText();

        assertApiCode(mockMvc.perform(post("/api/seller/goods/{id}/off-shelf", a2)
                .header("Authorization", bearer(token))).andReturn(), 0);

        MvcResult onSaleUnderL1 = mockMvc.perform(get("/api/seller/goods")
                        .param("status", "ON_SALE")
                        .param("category_id", categoryA.getL1().getId())
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(onSaleUnderL1, 0);
        assertThat(data(onSaleUnderL1).path("total").asLong()).isEqualTo(1);
        assertThat(data(onSaleUnderL1).path("items").get(0).path("id").asText()).isEqualTo(a1);

        MvcResult offManual = mockMvc.perform(get("/api/seller/goods")
                        .param("status", "OFF_MANUAL")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(offManual, 0);
        assertThat(data(offManual).path("total").asLong()).isEqualTo(1);
        assertThat(data(offManual).path("items").get(0).path("id").asText()).isEqualTo(a2);

        MvcResult underL2 = mockMvc.perform(get("/api/seller/goods")
                        .param("category_id", categoryB.getId())
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(underL2, 0);
        assertThat(data(underL2).path("total").asLong()).isEqualTo(1);
        assertThat(data(underL2).path("items").get(0).path("id").asText()).isEqualTo(b1);

        MvcResult all = mockMvc.perform(get("/api/seller/goods")
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(all, 0);
        assertThat(data(all).path("total").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("库存调整：在售改 0 自动下架，清零下架改大于 0 自动上架")
    void updatesStockAndTriggersOffShelfTransitions() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        String id = data(publish(token, goodsBody(unique("库存-"), "10", 3, category.getId())))
                .path("id").asText();

        MvcResult toOne = updateStock(token, id, 1);
        assertApiCode(toOne, 0);
        assertThat(data(toOne).path("status").asText()).isEqualTo("ON_SALE");
        assertThat(data(toOne).path("stock").asInt()).isEqualTo(1);

        MvcResult toZero = updateStock(token, id, 0);
        assertApiCode(toZero, 0);
        assertThat(data(toZero).path("status").asText()).isEqualTo("OFF_ZERO");
        assertThat(data(toZero).path("stock").asInt()).isZero();
        assertApiCode(updateStock(token, id, 0), 20015);

        MvcResult publicAfterZero = mockMvc.perform(get("/api/public/goods/{id}", id)).andReturn();
        assertApiStatusAndCode(publicAfterZero, 404, 20011);

        MvcResult restocked = updateStock(token, id, 2);
        assertApiCode(restocked, 0);
        assertThat(data(restocked).path("status").asText()).isEqualTo("ON_SALE");
        assertThat(data(restocked).path("stock").asInt()).isEqualTo(2);

        MvcResult negative = updateStock(token, id, -1);
        assertApiCode(negative, 20015);
    }

    @Test
    @DisplayName("队列非空时不能把库存改为 0，也不能手动下架")
    void rejectsLeavingSaleWithActiveQueue() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        String id = data(publish(token, goodsBody(unique("排队-"), "10", 3, category.getId())))
                .path("id").asText();
        addQueuedIntention(id);

        assertApiCode(updateStock(token, id, 0), 20022);
        assertApiCode(postWithoutBody("/api/seller/goods/" + id + "/off-shelf", token), 20022);
    }

    @Test
    @DisplayName("手动下架后可重新上架，重新上架必须显式确认")
    void manuallyOffShelvesAndRelists() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        String id = data(publish(token, goodsBody(unique("下架-"), "10", 2, category.getId())))
                .path("id").asText();

        MvcResult offShelf = postWithoutBody("/api/seller/goods/" + id + "/off-shelf", token);
        assertApiCode(offShelf, 0);
        assertThat(data(offShelf).path("status").asText()).isEqualTo("OFF_MANUAL");
        assertThat(data(offShelf).path("stock").asInt()).isEqualTo(2);

        assertApiStatusAndCode(
                mockMvc.perform(get("/api/public/goods/{id}", id)).andReturn(), 404, 20011);
        assertApiCode(postWithoutBody("/api/seller/goods/" + id + "/off-shelf", token), 20019);
        assertApiCode(updateStock(token, id, 3), 20023);

        MvcResult noConfirm = mockMvc.perform(post("/api/seller/goods/{id}/re-list", id)
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(noConfirm, 20028);

        MvcResult falseConfirm = mockMvc.perform(post("/api/seller/goods/{id}/re-list", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirm", false))))
                .andReturn();
        assertApiCode(falseConfirm, 20028);

        MvcResult reListed = mockMvc.perform(post("/api/seller/goods/{id}/re-list", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirm", true))))
                .andReturn();
        assertApiCode(reListed, 0);
        assertThat(data(reListed).path("status").asText()).isEqualTo("ON_SALE");
        assertThat(data(reListed).path("stock").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("商品可以改挂二级类别，交易中禁止")
    void changesCategoryUnlessFrozen() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 first = createCategory();
        CategoryL2 second = createCategory();
        String id = data(publish(token, goodsBody(unique("改挂-"), "10", 1, first.getId())))
                .path("id").asText();

        MvcResult changed = changeCategory(token, id, second.getId());
        assertApiCode(changed, 0);
        assertThat(data(changed).path("category").path("l2_id").asText())
                .isEqualTo(second.getId());

        setStatus(id, GoodsStatus.FROZEN);
        assertApiCode(changeCategory(token, id, first.getId()), 20021);

        setStatus(id, GoodsStatus.OFF_MANUAL);
        MvcResult offShelfChanged = changeCategory(token, id, first.getId());
        assertApiCode(offShelfChanged, 0);
        assertThat(data(offShelfChanged).path("category").path("l2_id").asText())
                .isEqualTo(first.getId());

        assertApiStatusAndCode(changeCategory(token, id, "C2-not-exist"), 404, 20001);
        assertApiCode(changeCategory(token, id, first.getL1().getId()), 20002);
    }

    @Test
    @DisplayName("公共列表只展示在售，交易中详情可直链只读，下架后详情不可打开")
    void enforcesPublicVisibilityMatrix() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 category = createCategory();
        String id = data(publish(token, goodsBody(unique("可见性-"), "10", 1, category.getId())))
                .path("id").asText();

        MvcResult listOnSale = mockMvc.perform(get("/api/public/goods").param("page_size", "100"))
                .andReturn();
        assertApiCode(listOnSale, 0);
        assertThat(containsGoods(data(listOnSale), id)).isTrue();

        setStatus(id, GoodsStatus.FROZEN);
        MvcResult listFrozen = mockMvc.perform(get("/api/public/goods").param("page_size", "100"))
                .andReturn();
        assertApiCode(listFrozen, 0);
        assertThat(containsGoods(data(listFrozen), id)).isFalse();

        MvcResult frozenDetail = mockMvc.perform(get("/api/public/goods/{id}", id)).andReturn();
        assertApiCode(frozenDetail, 0);
        assertThat(data(frozenDetail).path("status").asText()).isEqualTo("FROZEN");
        assertThat(data(frozenDetail).path("purchasable").asBoolean()).isFalse();
        assertApiCode(updateStock(token, id, 2), 20021);
        assertApiCode(postWithoutBody("/api/seller/goods/" + id + "/off-shelf", token), 20021);

        setStatus(id, GoodsStatus.OFF_MANUAL);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/public/goods/{id}", id)).andReturn(), 404, 20011);

        MvcResult sellerDetail = mockMvc.perform(get("/api/seller/goods/{id}", id)
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(sellerDetail, 0);
        assertThat(data(sellerDetail).path("status").asText()).isEqualTo("OFF_MANUAL");
    }

    @Test
    @DisplayName("公共搜索与分页、一级类别包含其下二级商品")
    void publicSearchPaginatesAndFiltersByL1() throws Exception {
        String token = loginSeller(SELLER_PASSWORD);
        CategoryL2 first = createCategory();
        CategoryL2 second = categoryL2s.saveAndFlush(
                CategoryL2.create(first.getL1(), unique("二级-")));
        String keyword = unique("搜索-");
        String one = data(publish(token, goodsBody(keyword + "-1", "10", 1, first.getId())))
                .path("id").asText();
        data(publish(token, goodsBody(keyword + "-2", "10", 1, first.getId())));
        data(publish(token, goodsBody(keyword + "-3", "10", 1, second.getId())));

        MvcResult pageOne = mockMvc.perform(get("/api/public/goods")
                        .param("keyword", keyword)
                        .param("page", "1")
                        .param("page_size", "2"))
                .andReturn();
        assertApiCode(pageOne, 0);
        assertThat(data(pageOne).path("total").asLong()).isEqualTo(3);
        assertThat(data(pageOne).path("items").size()).isEqualTo(2);

        MvcResult pageTwo = mockMvc.perform(get("/api/public/goods")
                        .param("keyword", keyword)
                        .param("page", "2")
                        .param("page_size", "2"))
                .andReturn();
        assertApiCode(pageTwo, 0);
        assertThat(data(pageTwo).path("items").size()).isEqualTo(1);

        MvcResult l1Filter = mockMvc.perform(get("/api/public/goods")
                        .param("keyword", keyword)
                        .param("category_id", first.getL1().getId())
                        .param("page_size", "100"))
                .andReturn();
        assertApiCode(l1Filter, 0);
        assertThat(data(l1Filter).path("total").asLong()).isEqualTo(3);

        MvcResult l2Filter = mockMvc.perform(get("/api/public/goods")
                        .param("keyword", keyword)
                        .param("category_id", second.getId())
                        .param("page_size", "100"))
                .andReturn();
        assertApiCode(l2Filter, 0);
        assertThat(data(l2Filter).path("total").asLong()).isEqualTo(1);

        MvcResult byKeyword = mockMvc.perform(get("/api/public/goods")
                        .param("keyword", keyword + "-1"))
                .andReturn();
        assertApiCode(byKeyword, 0);
        assertThat(data(byKeyword).path("total").asLong()).isEqualTo(1);
        assertThat(data(byKeyword).path("items").get(0).path("id").asText()).isEqualTo(one);

        MvcResult badPageSize = mockMvc.perform(get("/api/public/goods")
                        .param("page_size", "101"))
                .andReturn();
        assertApiStatusAndCode(badPageSize, 400, 50002);
    }

    @Test
    @DisplayName("卖家接口要求卖家令牌，未知商品返回 404")
    void sellerGoodsRequiresSellerRoleAndReturns404() throws Exception {
        String sellerToken = loginSeller(SELLER_PASSWORD);

        MvcResult anonymous = mockMvc.perform(get("/api/seller/goods")).andReturn();
        assertApiStatusAndCode(anonymous, 401, 10002);

        String buyer = unique("buyer-");
        registerBuyer(buyer, "Buyer@1234", "13800000000", null);
        String buyerToken = loginBuyer(buyer, "Buyer@1234");
        MvcResult buyerAttempt = mockMvc.perform(get("/api/seller/goods")
                        .header("Authorization", bearer(buyerToken)))
                .andReturn();
        assertApiStatusAndCode(buyerAttempt, 401, 10002);

        assertApiCode(mockMvc.perform(get("/api/public/goods")).andReturn(), 0);

        assertApiStatusAndCode(
                mockMvc.perform(get("/api/seller/goods/{id}", "G-not-exist")).andReturn(),
                401, 10002);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/seller/goods/{id}", "G-not-exist")
                        .header("Authorization", bearer(sellerToken))).andReturn(),
                404, 20011);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/public/goods/{id}", "G-not-exist")).andReturn(),
                404, 20011);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/seller/goods")
                        .param("status", "UNKNOWN")
                        .header("Authorization", bearer(sellerToken)))
                        .andReturn(),
                400, 50002);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/public/goods")
                        .param("category_id", "C2-not-exist"))
                        .andReturn(),
                404, 20001);
        assertApiStatusAndCode(
                mockMvc.perform(get("/api/public/goods").param("page", "0")).andReturn(),
                400, 50002);
    }

    private Map<String, Object> goodsBody(
            String name, String price, int stock, String categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("price", new BigDecimal(price));
        body.put("stock", stock);
        body.put("category_id", categoryId);
        body.put("description", List.of());
        body.put("main_image", null);
        body.put("gallery_images", List.of());
        return body;
    }

    private MvcResult publish(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/seller/goods")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn();
    }

    private MvcResult updateStock(String token, String id, int stock) throws Exception {
        return mockMvc.perform(put("/api/seller/goods/{id}/stock", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stock", stock))))
                .andReturn();
    }

    private MvcResult changeCategory(String token, String id, String categoryId) throws Exception {
        return mockMvc.perform(put("/api/seller/goods/{id}/category", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category_id", categoryId))))
                .andReturn();
    }

    private MvcResult postWithoutBody(String path, String token) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(token))).andReturn();
    }

    private String upload(String token, String purpose, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/seller/media")
                        .file(file)
                        .param("purpose", purpose)
                        .header("Authorization", bearer(token)))
                .andReturn();
        assertApiCode(result, 0);
        return data(result).path("url").asText();
    }

    private void addQueuedIntention(String goodsId) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        BuyerAccount buyer = createBuyer();
        intentions.saveAndFlush(Intention.submit(goods, buyer, 1));
    }

    private void setStatus(String goodsId, GoodsStatus status) {
        Goods goods = goodsRepository.findById(goodsId).orElseThrow();
        goods.changeStatus(status);
        goodsRepository.saveAndFlush(goods);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private boolean containsGoods(JsonNode page, String goodsId) {
        for (JsonNode item : page.path("items")) {
            if (goodsId.equals(item.path("id").asText())) {
                return true;
            }
        }
        return false;
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
