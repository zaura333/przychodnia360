package pl.przychodnia360;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Reguły architektury. Napisane tak, żeby działały od razu przy pustych modułach i nie wymagały zmian w 1a
 * (stąd {@code allowEmptyShould}). Moduły domenowe mają pakiety api, application, domain, infrastructure.
 */
@AnalyzeClasses(packages = "pl.przychodnia360", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "pl.przychodnia360";
    private static final List<String> MODULES = List.of(
            "identity", "tenant", "catalog", "staff", "appointments", "patients", "records", "audit",
            "notifications", "health");

    @ArchTest
    static final ArchRule noCyclesBetweenModules = slices()
            .matching(BASE + ".(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersDoNotCallRepositories = noClasses()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedCoreIsIndependent = noClasses()
            .that().resideInAPackage(BASE + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    BASE + ".common..", BASE + ".identity..", BASE + ".tenant..", BASE + ".health..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedCoreHasNoSpring = classes()
            .that().resideInAPackage(BASE + ".shared..")
            .should().onlyDependOnClassesThat().resideOutsideOfPackage("org.springframework..")
            .allowEmptyShould(true);

    /** Moduł wywołuje inny wyłącznie przez jego pakiety api i application, nigdy przez domain ani infrastructure. */
    @ArchTest
    static final ArchRule modulesAreAccessedOnlyThroughApiOrApplication = noClasses()
            .that(new com.tngtech.archunit.base.DescribedPredicate<>("są w innym module") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaClass c) {
                    return true;
                }
            })
            .should(new com.tngtech.archunit.lang.ArchCondition<>("zależeć od cudzego domain lub infrastructure") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaClass origin,
                        com.tngtech.archunit.lang.ConditionEvents events) {
                    String originModule = module(origin.getPackageName());
                    for (var dependency : origin.getDirectDependenciesFromSelf()) {
                        String targetPackage = dependency.getTargetClass().getPackageName();
                        String targetModule = module(targetPackage);
                        boolean internal = targetPackage.contains(".domain") || targetPackage.contains(".infrastructure");
                        if (targetModule != null && originModule != null && !targetModule.equals(originModule)
                                && MODULES.contains(targetModule) && internal) {
                            events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(dependency,
                                    dependency.getDescription()));
                        }
                    }
                }
            })
            .allowEmptyShould(true);

    private static String module(String packageName) {
        if (!packageName.startsWith(BASE + ".")) {
            return null;
        }
        String rest = packageName.substring(BASE.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
