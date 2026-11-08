package com.simpleshop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * 包依赖门禁：先立四项硬约束，后续每步只收紧不放宽。
 *
 * <p>order / cart / favorite 目前还没有类，规则对空包恒成立；提前登记是为了
 * B 阶段生长时不必再改门禁本身。
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
                            "com.simpleshop.favorite..");

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
                            "com.simpleshop.favorite.api..")
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
                            "com.simpleshop.favorite.repository..");

    @ArchTest
    static final ArchRule goods_must_not_depend_on_order_or_cart =
            noClasses().that().resideInAPackage("com.simpleshop.goods..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.order..",
                            "com.simpleshop.cart..",
                            "com.simpleshop.favorite..");

    /** cart 与 favorite 还没有类，规则在 B2 生长前天然为空，允许空集通过。 */
    @ArchTest
    static final ArchRule bulk_features_must_not_depend_on_order =
            noClasses().that().resideInAnyPackage(
                            "com.simpleshop.cart..",
                            "com.simpleshop.favorite..")
                    .should().dependOnClassesThat().resideInAPackage("com.simpleshop.order..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule feature_packages_must_not_have_cycles =
            SlicesRuleDefinition.slices()
                    .matching("com.simpleshop.(*)..")
                    .should().beFreeOfCycles();
}
