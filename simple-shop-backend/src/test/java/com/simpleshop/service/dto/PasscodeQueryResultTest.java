package com.simpleshop.service.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 三态查询结果的<b>类型级约束</b>验证（{@code BR-29}、{@code INV-07}、{@code AC-11}）。
 *
 * <p>本类要回答的问题是：「口令码失效时不泄漏信息」这条规则，是不是**靠类型挡住**的，
 * 而不是靠「记得别塞字段」。故这里直接检查 record 的<b>组件清单</b>——
 * 若有人给 {@code Expired} 加一个 {@code status} 字段，用例立刻失败。
 */
class PasscodeQueryResultTest {

    // -------------------------------------------------------------------------
    // BR-29：失效与不存在不得携带任何买家/商品信息（类型级）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("BR-29：Expired 是【零字段】记录——想「顺手带出失败原因」都没有地方放")
    void expiredCarriesNoFieldsAtAll() {
        assertThat(PasscodeQueryResult.Expired.class.getRecordComponents())
                .as("一旦有人加上 status/failType/name/tel，本断言失败：那等于把 BR-29 的防线让掉")
                .isEmpty();
    }

    @Test
    @DisplayName("BR-29：NotFound 同样是零字段记录")
    void notFoundCarriesNoFieldsAtAll() {
        assertThat(PasscodeQueryResult.NotFound.class.getRecordComponents()).isEmpty();
    }

    @Test
    @DisplayName("只有 Active 带数据，且只有 rank 与 trading 两项（不涉及他人信息）")
    void onlyActiveCarriesData() {
        RecordComponent[] components = PasscodeQueryResult.Active.class.getRecordComponents();
        assertThat(components)
                .extracting(RecordComponent::getName)
                .containsExactly("rank", "trading");
    }

    // -------------------------------------------------------------------------
    // AC-11：位次与「已进入交易」互斥
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC-11：trading 与 rank 互斥——Active(3, true) 这种自相矛盾对象构造不出来")
    void activeRejectsContradictoryCombinations() {
        assertThatThrownBy(() -> new PasscodeQueryResult.Active(3, true))
                .as("既在交易中又排第 3 位是自相矛盾的，应拒绝构造")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("互斥");

        assertThatThrownBy(() -> new PasscodeQueryResult.Active(null, false))
                .as("排队中却没有位次同样矛盾")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-11：合法组合可构造：queued 带位次、trading 无位次")
    void activeAcceptsConsistentCombinations() {
        assertThat(new PasscodeQueryResult.Active(1, false).rank()).isEqualTo(1);
        assertThat(new PasscodeQueryResult.Active(null, true).trading()).isTrue();
    }

    // -------------------------------------------------------------------------
    // prompt() 是全函数
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("三态各有明确的提示码（prompt() 不返回 null，与 BuyerResult 的便利方法不同）")
    void promptIsTotal() {
        assertThat(new PasscodeQueryResult.NotFound().prompt()).isEqualTo(BuyerPrompt.PASSCODE_NOT_FOUND);
        assertThat(new PasscodeQueryResult.Expired().prompt()).isEqualTo(BuyerPrompt.PASSCODE_EXPIRED);
        assertThat(new PasscodeQueryResult.Active(2, false).prompt()).isEqualTo(BuyerPrompt.QUEUED_RANK);
        assertThat(new PasscodeQueryResult.Active(null, true).prompt()).isEqualTo(BuyerPrompt.ENTERED_TRADE);
    }

    @Test
    @DisplayName("BR-29：失效文案【不】提及原因或结果（只断言「不许出现什么」，不钉死「必须是什么」）")
    void expiredTextRevealsNothing() {
        String expired = BuyerPrompt.PASSCODE_EXPIRED.text();
        // 不得出现「已售出/交易失败/已撤销/成功」等结果线索，也不得出现买家字段名。
        // ⚠️ 这里刻意【不】写「文案必须等于某一句」——文案的权威是第 10 章 §10.5，
        //    在测试里再抄一份等于制造第二个权威（详见 BuyerPrompt 的类注释）。
        assertThat(expired)
                .doesNotContain("售出", "成功", "失败", "撤销", "姓名", "电话", "商品");
    }
}
