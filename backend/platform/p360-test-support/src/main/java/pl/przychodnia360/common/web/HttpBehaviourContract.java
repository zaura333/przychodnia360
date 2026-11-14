package pl.przychodnia360.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;

/** Zachowanie HTTP całej aplikacji: identyfikator żądania, Problem Details, nagłówki, publiczne endpointy. */
public abstract class HttpBehaviourContract extends PostgresIntegrationTest {

    @Test
    void healthIsPublicAndReturnsRequestId() throws Exception {
        var response = get("/api/v1/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void healthDbQueriesDatabase() throws Exception {
        var response = get("/api/v1/health/db");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void validIncomingRequestIdIsEchoed() throws Exception {
        var response = get("/api/v1/health", "X-Request-Id", "abc12345-test");

        assertThat(response.headers().firstValue("X-Request-Id")).contains("abc12345-test");
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        var response = get("/api/v1/health", "X-Request-Id", "zly id\twstrzykniecie");

        assertThat(response.headers().firstValue("X-Request-Id").orElseThrow()).matches("[0-9a-f-]{36}");
    }

    @Test
    void anonymousMeIsRejectedWithProblemDetails() throws Exception {
        var response = get("/api/v1/me", "X-Request-Id", "trace-0001-abc");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"status\":401")
                .contains("/problems/unauthorized")
                .contains("\"traceId\":\"trace-0001-abc\"");
    }

    @Test
    void unknownPathsDoNotRevealExistenceToAnonymous() throws Exception {
        assertThat(get("/api/v1/nie-ma-takiego").statusCode()).isEqualTo(401);
    }

    @Test
    void facilitiesAreListedPublicly() throws Exception {
        var response = get("/api/v1/facilities");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("[");
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        var headers = get("/api/v1/health").headers();

        assertThat(headers.firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(headers.firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    void errorResponsesDoNotLeakInternals() throws Exception {
        var body = get("/api/v1/me").body();

        assertThat(body).doesNotContain("Exception").doesNotContain("at pl.przychodnia360").doesNotContain("SQL");
    }
}
