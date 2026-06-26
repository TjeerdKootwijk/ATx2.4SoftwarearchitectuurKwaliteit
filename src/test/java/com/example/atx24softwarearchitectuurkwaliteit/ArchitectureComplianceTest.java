package com.example.atx24softwarearchitectuurkwaliteit;

import com.example.atx24softwarearchitectuurkwaliteit.provider.MessagingProvider;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectuur-compliancetesten (ArchUnit) — bewijst dat de codebase de afgesproken
 * ontwerpprincipes automatisch naleeft, zodat afwijkingen bij elke build worden
 * gedetecteerd.
 *
 * Gedekte principes:
 *   OCP — alle concrete providers implementeren MessagingProvider (ADR-8)
 *   SRP — services zijn niet afhankelijk van de controllerlaag
 *   Layering — DAO-implementaties zitten in het impl-subpackage
 */
@AnalyzeClasses(packages = "com.example.atx24softwarearchitectuurkwaliteit")
class ArchitectureComplianceTest {

    /**
     * Open/Closed Principle (OCP) — ADR-8 / Strategy-patroon.
     *
     * Elke concrete klasse met de naam *Provider in het provider-package moet
     * de MessagingProvider-interface implementeren. Dit garandeert dat een nieuwe
     * provider kan worden toegevoegd door uitsluitend een nieuwe klasse te schrijven —
     * zonder bestaande code te wijzigen (OCP).
     *
     * De MessagingProviderFactory registreert providers automatisch via Spring
     * dependency injection op basis van deze interface.
     */
    @ArchTest
    static final ArchRule alle_concrete_providers_implementeren_messaging_provider_interface =
            classes()
                    .that().resideInAPackage("..provider..")
                    .and().haveSimpleNameEndingWith("Provider")
                    .and().areNotInterfaces()
                    .should().implement(MessagingProvider.class)
                    .because("Elke provider moet het Strategy-patroon volgen zodat " +
                             "de MessagingProviderFactory providers uitwisselbaar kan inzetten (OCP, ADR-8)");

    /**
     * Single Responsibility Principle (SRP) — laagscheiding.
     *
     * Service-klassen mogen nooit direct afhangen van controller-klassen.
     * De servicelaag bevat bedrijfslogica; de controllerlaag verzorgt
     * HTTP-presentatie. Deze scheiding maakt de servicelaag testbaar
     * zonder HTTP-context en voorkomt circulaire afhankelijkheden.
     */
    @ArchTest
    static final ArchRule service_laag_mag_niet_afhangen_van_controller_laag =
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..controller..")
                    .because("Services mogen niet afhankelijk zijn van de presentatielaag (SRP)");

    /**
     * Layered architecture — DAO-implementaties in impl-subpackage.
     *
     * De interface (NotificationLogDAO, TenantDAO) staat in het dao-package;
     * de implementatie (NotificationLogDAOImpl, TenantDAOImpl) staat in dao.impl.
     * Dit maakt het mogelijk om implementaties te vervangen zonder interfaces te wijzigen.
     */
    @ArchTest
    static final ArchRule dao_implementaties_zitten_in_impl_subpackage =
            classes()
                    .that().haveSimpleNameEndingWith("DAOImpl")
                    .should().resideInAPackage("..dao.impl..")
                    .because("DAO-implementaties worden in het impl-subpackage geplaatst " +
                             "zodat interface en implementatie gescheiden blijven");
}
