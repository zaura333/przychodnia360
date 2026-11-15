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
@AnalyzeClasses(packages = "pl.przychodnia360",
        importOptions = {ImportOption.DoNotIncludeTests.class, ArchitectureTest.ExcludeTestSupport.class})
class ArchitectureTest {

    /**
     * Biblioteka testowa (p360-test-support) nie należy do architektury aplikacji: jej klasy leżą w pakietach
     * pl.przychodnia360.common.* (dostęp do składowych pakietowych) i zależą od startera, więc w analizie cykli
     * tworzyłyby sztuczny cykl common - testsupport.
     */
    public static final class ExcludeTestSupport implements ImportOption {
        @Override
        public boolean includes(com.tngtech.archunit.core.importer.Location location) {
            return !location.contains("p360-test-support");
        }
    }

    private static final String BASE = "pl.przychodnia360";
    private static final List<String> MODULES = List.of(
            "identity", "tenant", "patients", "health");

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

    /** Moduł wywołuje inny wyłącznie przez jego pakiet api (0R.2); application, domain i infrastructure są prywatne. */
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
                        boolean internal = targetPackage.contains(".domain") || targetPackage.contains(".infrastructure")
                                || targetPackage.contains(".application");
                        if (targetModule != null && originModule != null && !targetModule.equals(originModule)
                                && MODULES.contains(targetModule) && internal) {
                            events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(dependency,
                                    dependency.getDescription()));
                        }
                    }
                }
            })
            .allowEmptyShould(true);

    /** Zdarzenia domenowe niosą tylko identyfikatory i dane techniczne (ADR-M10), bez danych osobowych. */
    @ArchTest
    static final ArchRule domainEventsCarryNoPersonalData = classes()
            .that().implement(pl.przychodnia360.shared.DomainEvent.class)
            .and().areNotInterfaces()
            .should(new com.tngtech.archunit.lang.ArchCondition<>("nie mieć pól z danymi osobowymi") {
                private final java.util.regex.Pattern pii = java.util.regex.Pattern.compile(
                        "(?i).*(first|last)name|.*pesel.*|.*email.*|.*phone.*|.*birth.*|.*address.*|.*password.*");

                @Override
                public void check(com.tngtech.archunit.core.domain.JavaClass event,
                        com.tngtech.archunit.lang.ConditionEvents events) {
                    for (var field : event.getAllFields()) {
                        if (pii.matcher(field.getName()).matches()) {
                            events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(field,
                                    event.getName() + " ma pole " + field.getName()));
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
