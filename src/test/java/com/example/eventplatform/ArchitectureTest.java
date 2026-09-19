package com.example.eventplatform;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Regras de arquitetura executadas a cada build: impedem a erosão do desenho hexagonal
 * à medida que o time e o código crescem.
 */
@AnalyzeClasses(packages = "com.example.eventplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreIsFrameworkFree = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.apache.kafka..",
                    "com.fasterxml.jackson..", "io.micrometer..")
            .because("o núcleo deve ser testável e evoluir independentemente de frameworks");

    @ArchTest
    static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapters").definedBy("..adapter..")
            .layer("Config").definedBy("..config..")
            .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config");

    @ArchTest
    static final ArchRule adaptersDoNotTalkToEachOther = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence..")
            .because("adapters de entrada usam portas, não implementações de saída");
}
