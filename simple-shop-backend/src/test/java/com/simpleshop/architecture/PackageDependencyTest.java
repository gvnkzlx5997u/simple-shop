package com.simpleshop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * 包依赖门禁：先立几条硬约束，后续每步只收紧不放宽。
 *
 * <p>阶段四新增 address / payment / aftersale 三个功能包，方向固定：
 * order 可以用 address.api、payment.api、goods.api；payment 谁都不认识；
 * aftersale 只能用 order.api（以及 order.enums 里的状态枚举）。
 * 反向依赖一律禁止，环由切片规则兜底。
 */
@AnalyzeClasses(packages = "com.simpleshop", importOptions = ImportOption.DoNotIncludeTests.class)
class PackageDependencyTest {

    @ArchTest
    static final ArchRule common_must_not_depend_on_feature_packages =
            noClasses().that().resideInAPackage("com.simpleshop.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.account..",
                            "com.simpleshop.category..",
                            "com.simpleshop.goods..",
                            "com.simpleshop.inventory..",
                            "com.simpleshop.order..",
                            "com.simpleshop.cart..",
                            "com.simpleshop.favorite..",
                            "com.simpleshop.address..",
                            "com.simpleshop.payment..",
                            "com.simpleshop.aftersale..");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule feature_api_must_not_depend_on_implementation =
            noClasses().that().resideInAnyPackage(
                            "com.simpleshop.account.api..",
                            "com.simpleshop.category.api..",
                            "com.simpleshop.goods.api..",
                            "com.simpleshop.inventory.api..",
                            "com.simpleshop.order.api..",
                            "com.simpleshop.cart.api..",
                            "com.simpleshop.favorite.api..",
                            "com.simpleshop.address.api..",
                            "com.simpleshop.payment.api..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.account.service..",
                            "com.simpleshop.account.entity..",
                            "com.simpleshop.account.repository..",
                            "com.simpleshop.category.service..",
                            "com.simpleshop.category.entity..",
                            "com.simpleshop.category.repository..",
                            "com.simpleshop.goods.service..",
                            "com.simpleshop.goods.entity..",
                            "com.simpleshop.goods.repository..",
                            "com.simpleshop.inventory.service..",
                            "com.simpleshop.inventory.entity..",
                            "com.simpleshop.inventory.repository..",
                            "com.simpleshop.order.service..",
                            "com.simpleshop.order.entity..",
                            "com.simpleshop.order.repository..",
                            "com.simpleshop.cart.service..",
                            "com.simpleshop.cart.entity..",
                            "com.simpleshop.cart.repository..",
                            "com.simpleshop.favorite.service..",
                            "com.simpleshop.favorite.entity..",
                            "com.simpleshop.favorite.repository..",
                            "com.simpleshop.address.service..",
                            "com.simpleshop.address.entity..",
                            "com.simpleshop.address.repository..",
                            "com.simpleshop.payment.service..",
                            "com.simpleshop.payment.entity..",
                            "com.simpleshop.payment.repository..");

    /**
     * 商品、购物车、收藏、账号不参与交易：它们不该认识订单、支付、售后与地址。
     *
     * <p>阶段三这条只约束了 goods 对 order/cart 的依赖；阶段四把 payment、aftersale、address
     * 也纳入——商品包如果哪天 import 了支付接口，说明分层已经乱了。
     */
    @ArchTest
    static final ArchRule storefront_features_must_not_depend_on_trade_features =
            noClasses().that().resideInAnyPackage(
                            "com.simpleshop.goods..",
                            "com.simpleshop.cart..",
                            "com.simpleshop.favorite..",
                            "com.simpleshop.account..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.order..",
                            "com.simpleshop.payment..",
                            "com.simpleshop.aftersale..",
                            "com.simpleshop.address..");

    /**
     * 支付包是叶子：它只依赖自身、common 与外部库。
     *
     * <p>一旦 payment 认识 order，「支付包能被单独替换」就不成立了——接真实渠道时会连带改订单。
     */
    @ArchTest
    static final ArchRule payment_must_not_depend_on_business_features =
            noClasses().that().resideInAPackage("com.simpleshop.payment..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.order..",
                            "com.simpleshop.goods..",
                            "com.simpleshop.inventory..",
                            "com.simpleshop.aftersale..",
                            "com.simpleshop.address..",
                            "com.simpleshop.cart..",
                            "com.simpleshop.favorite..",
                            "com.simpleshop.account..",
                            "com.simpleshop.category..");

    /**
     * 售后只能通过 order.api 认识订单，不能碰实体、仓储、服务与 DTO。
     *
     * <p>例外是 {@code order.enums}：订单状态是跨包共享的取值类型，售后要判断「订单是否交易完成」，
     * 复制一份枚举只会引入第二套状态口径。
     */
    @ArchTest
    static final ArchRule aftersale_must_not_depend_on_order_internals =
            noClasses().that().resideInAPackage("com.simpleshop.aftersale..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.order.service..",
                            "com.simpleshop.order.entity..",
                            "com.simpleshop.order.repository..",
                            "com.simpleshop.order.dto..",
                            "com.simpleshop.order.controller..",
                            "com.simpleshop.order.support..");

    @ArchTest
    static final ArchRule feature_packages_must_not_have_cycles =
            SlicesRuleDefinition.slices()
                    .matching("com.simpleshop.(*)..")
                    .should().beFreeOfCycles();
}
