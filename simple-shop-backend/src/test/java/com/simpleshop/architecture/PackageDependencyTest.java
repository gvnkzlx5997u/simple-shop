package com.simpleshop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * 包依赖门禁：先立四项硬约束，后续每步只收紧不放宽。
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
                            "com.simpleshop.intention..",
                            "com.simpleshop.history..");

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
                            "com.simpleshop.intention.api..",
                            "com.simpleshop.history.api..")
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
                            "com.simpleshop.intention.service..",
                            "com.simpleshop.intention.entity..",
                            "com.simpleshop.intention.repository..",
                            "com.simpleshop.history.service..",
                            "com.simpleshop.history.entity..",
                            "com.simpleshop.history.repository..");

    @ArchTest
    static final ArchRule intention_must_only_depend_on_goods_api =
            noClasses().that().resideInAPackage("com.simpleshop.intention..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.simpleshop.goods.entity..",
                            "com.simpleshop.goods.service..",
                            "com.simpleshop.goods.repository..",
                            "com.simpleshop.goods.dto..");

    @ArchTest
    static final ArchRule goods_must_not_depend_on_intention =
            noClasses().that().resideInAPackage("com.simpleshop.goods..")
                    .should().dependOnClassesThat().resideInAPackage("com.simpleshop.intention..");

    @ArchTest
    static final ArchRule feature_packages_must_not_have_cycles =
            SlicesRuleDefinition.slices()
                    .matching("com.simpleshop.(*)..")
                    .should().beFreeOfCycles();
}
