package com.simpleshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.simpleshop.persistence.repository.IntentionRepository;
import com.simpleshop.testing.ShopFixtures;

/**
 * {@code PasscodeService.generateUnique()} 的行为验证（{@code FR-14}、{@code S7-02}）——需要真库。
 *
 * <h2>⚠️ 冲突重试与耗尽分支本来「不可验证」</h2>
 * <p>真实随机下撞码概率约 {@code 1/36¹²} ≈ {@code 2×10⁻¹⁹}，那两段代码永远跑不到。
 * 故 {@code PasscodeService} 留了一个包内可见的重载可注入随机源，
 * 本类用一个<b>固定输出同一个码</b>的 {@link SecureRandom} 子类走通：
 * <ul>
 *   <li>库里已有该码 → 触发重试；</li>
 *   <li>重试到上限仍冲突 → 抛 {@link IllegalStateException}（而不是返回一个重复的码）。</li>
 * </ul>
 * <p>另有一个刻意<b>不</b>做的事：方案 §3.3.7 说要「捕获 {@code DataIntegrityViolationException}
 * 做一次重试」，本实现没有做，理由写在 {@code PasscodeService#generateUnique} 的注释里
 * （唯一约束冲突会让事务进入 rollback-only，同事务内重试必然得到
 * {@code UnexpectedRollbackException}）。此处不写「装作测过」的用例。
 */
@SpringBootTest
@ActiveProfiles("test")
class PasscodeGenerationTest {

    private static final String FIXED_PASSCODE = "AAAAAAAAAAAA";

    @Autowired
    private PasscodeService passcodeService;

    @Autowired
    private IntentionRepository intentionRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private ShopFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new ShopFixtures(jdbcTemplate);
        fixtures.cleanGoodsState();
        fixtures.resetSequence();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanGoodsState();
    }

    // -------------------------------------------------------------------------
    // 正常路径
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("生成的码恒为 12 位 [A-Z0-9]（C-16、BR-25）")
    void generatedPasscodesMatchContractShape() {
        for (int i = 0; i < 50; i++) {
            String token = passcodeService.generateUnique();
            assertThat(token)
                    .as("第 %d 个", i + 1)
                    .hasSize(12)
                    .matches("[A-Z0-9]{12}");
            assertThat(PasscodeService.isWellFormed(token))
                    .as("自己生成的码必须能通过自己的格式判定")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("连续生成 300 个互不相等（码空间 36¹²，撞码不该出现）")
    void generatedPasscodesAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            seen.add(passcodeService.generateUnique());
        }
        assertThat(seen).hasSize(300);
    }

    @Test
    @DisplayName("生成的码不会与库中已有口令码相同（查重环节确实在起作用）")
    void generatedPasscodesAvoidExistingTokens() {
        // 造 200 条已有意向，再生成 200 个码，两者不应有交集
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-占位商品", "10.00");
        fixtures.insertIntentions(goodsId, 200, "queued");

        Set<String> existing = new HashSet<>(intentionRepository.findAll().stream()
                .map(intention -> intention.getToken())
                .toList());
        assertThat(existing).hasSize(200);

        for (int i = 0; i < 200; i++) {
            assertThat(existing)
                    .as("新生成的码不得落在已有码集合里")
                    .doesNotContain(passcodeService.generateUnique());
        }
    }

    // -------------------------------------------------------------------------
    // 冲突分支（靠注入固定随机源才可验证）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⚠️ 库中已存在同一个码时：重试到上限后抛异常，绝不返回重复码")
    void collisionExhaustionThrowsInsteadOfReturningDuplicate() {
        // 让随机源固定产出 "AAAAAAAAAAAA"，并先把它插进库
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-占位商品", "10.00");
        fixtures.insertIntention(goodsId, 1, "queued", FIXED_PASSCODE);
        assertThat(intentionRepository.findByToken(FIXED_PASSCODE)).isPresent();

        PasscodeService fixedRandomService = new PasscodeService(
                intentionRepository, fixedOutputRandom());

        assertThatThrownBy(fixedRandomService::generateUnique)
                .as("5 次都撞到同一个已存在的码 → 必须失败，而不是把重复码交出去")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique passcode");
    }

    @Test
    @DisplayName("冲突重试：第 1 次撞码后仍能生成出【不同】的码（证明重试分支可达且有效）")
    void collisionRetriesAndEventuallySucceeds() {
        String goodsId = fixtures.insertGoods("on_sale", null, "GDS-占位商品", "10.00");
        fixtures.insertIntention(goodsId, 1, "queued", FIXED_PASSCODE);

        // 随机源：第 1 次返回固定码（必撞），之后返回另一个码（不再撞）
        PasscodeService firstCollides = new PasscodeService(
                intentionRepository, firstCallFixedRandom());

        String token = firstCollides.generateUnique();
        assertThat(token)
                .as("重试后拿到的必须是另一个码")
                .isNotEqualTo(FIXED_PASSCODE)
                .hasSize(12);
    }

    // -------------------------------------------------------------------------
    // 辅助：可控随机源
    // -------------------------------------------------------------------------

    /** 固定输出同一个码的随机源：{@code nextInt(bound)} 恒返回 0 → 每一位都取字符集第 0 个。 */
    private static SecureRandom fixedOutputRandom() {
        return new SecureRandom() {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    /**
     * 前 {@code LENGTH} 次调用输出 0（第 1 个码 = {@code "AAAAAAAAAAAA"}，必然撞库），
     * 此后输出 1（第 2 个码 = {@code "BBBBBBBBBBBB"}）。
     *
     * <p>⚠️ 计到 {@code LENGTH} 次而不是 1 次：{@code randomPasscode()} 每个码要调
     * <b>12 次</b> {@code nextInt}（每位一次）。若只让第一次返回 0，得到的首个码是
     * {@code "ABBBBBBBBBBB"} —— 与库里的 {@code "AAAAAAAAAAAA"} 不同，重试分支根本不会触发，
     * 用例就会「绿着什么都没测」。
     */
    private static SecureRandom firstCallFixedRandom() {
        return new SecureRandom() {
            private static final long serialVersionUID = 1L;
            private int calls = 0;

            @Override
            public int nextInt(int bound) {
                return calls++ < PasscodeService.LENGTH ? 0 : 1;
            }
        };
    }
}
