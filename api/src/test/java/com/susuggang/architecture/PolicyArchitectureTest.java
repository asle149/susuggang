package com.susuggang.architecture;

import com.susuggang.payment.PaymentPolicy;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// 정책 계층의 OCP·계층 분리를 리뷰 관례가 아니라 테스트로 강제한다 — 깨지는 코드는 빌드에서 떨어진다
@AnalyzeClasses(packages = "com.susuggang", importOptions = ImportOption.DoNotIncludeTests.class)
class PolicyArchitectureTest {

    // 서비스가 구현체를 알면 "정책 추가 = 서비스 무변경"이 무너진다 — 소비는 인터페이스(List 주입)로만
    @ArchTest
    static final ArchRule 정책_구현체는_인터페이스로만_소비된다 =
            noClasses()
                    .should().dependOnClassesThat(
                            com.tngtech.archunit.base.DescribedPredicate.describe(
                                    "PaymentPolicy 구현 클래스",
                                    c -> !c.isInterface() && c.isAssignableTo(PaymentPolicy.class)));

    // 정책은 컨텍스트만 보고 판정한다 — 조회·발행이 섞이면 정책 단위 테스트와 실행 순서 독립성이 깨진다
    @ArchTest
    static final ArchRule 정책은_저장소나_서비스에_의존하지_않는다 =
            classes()
                    .that().implement(PaymentPolicy.class)
                    .should().onlyDependOnClassesThat()
                    .resideOutsideOfPackages("com.susuggang.repository", "com.susuggang.service")
                    .andShould().onlyDependOnClassesThat()
                    .haveSimpleNameNotEndingWith("Service")
                    .andShould().onlyDependOnClassesThat()
                    .haveSimpleNameNotEndingWith("Repository");

    @ArchTest
    static final ArchRule 컨트롤러는_저장소를_직접_부르지_않는다 =
            noClasses()
                    .that().resideInAPackage("com.susuggang.controller..")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("Repository");
}
