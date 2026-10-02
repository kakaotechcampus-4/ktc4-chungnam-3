package com.ktc.chungnam3.remembrall.auth.security;

import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiErrorResponse;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key",
        "spring.ai.google.genai.embedding.api-key=test-api-key",
        "youtube.api.key=test-api-key",
        "kakao.app-id=1"
})
@Import(ApiErrorIntegrationTest.ProbeConfiguration.class)
class ApiErrorIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SessionTokenHasher sessionTokenHasher;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExceptionProbe exceptionProbe;

    @Autowired
    private ErrorDispatchProbe errorDispatchProbe;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String validToken;
    private String expiredToken;

    @BeforeEach
    void setUp() {
        Instant now = clock.instant();
        validToken = createSession(now.plusSeconds(3600), now);
        expiredToken = createSession(now.minusSeconds(1), now);
        exceptionProbe.lastException.set(null);
        errorDispatchProbe.count.set(0);
        errorDispatchProbe.originalStatus.set(null);
        errorDispatchProbe.dispatchPath.set(null);
    }

    @Test
    void malformedJsonReturns400WithExistingErrorFormat() throws Exception {
        HttpResponse<String> response = request("PUT", "/api/devices/fcm-token", validToken, "{\"fcmToken\":");

        assertThat(exceptionProbe.lastException.get()).isInstanceOf(HttpMessageNotReadableException.class);
        assertError(response, 400, "INVALID_REQUEST");
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @Test
    void invalidBodyStillReturns400WithExistingErrorFormat() throws Exception {
        assertError(request("PUT", "/api/devices/fcm-token", validToken, "{\"fcmToken\":\"\"}"),
                400, "INVALID_REQUEST");
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @Test
    void missingPathActuallyRaisesNoResourceFoundExceptionAndReturns404() throws Exception {
        HttpResponse<String> response = request("GET", "/api/missing-path", validToken, null);

        assertThat(exceptionProbe.lastException.get()).isInstanceOf(NoResourceFoundException.class);
        assertError(response, 404, "NOT_FOUND");
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @Test
    void unsupportedMethodReturns405AndPreservesSpringAllowHeader() throws Exception {
        HttpResponse<String> response = request("POST", "/api/devices/fcm-token", validToken, "{}");

        assertThat(exceptionProbe.lastException.get()).isInstanceOf(HttpRequestMethodNotSupportedException.class);
        HttpRequestMethodNotSupportedException exception =
                (HttpRequestMethodNotSupportedException) exceptionProbe.lastException.get();
        assertError(response, 405, "METHOD_NOT_ALLOWED");
        assertThat(response.headers().firstValue(HttpHeaders.ALLOW)).hasValue(
                exception.getHeaders().getFirst(HttpHeaders.ALLOW));
        assertThat(response.headers().firstValue(HttpHeaders.ALLOW).orElseThrow()).contains("PUT");
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "expired", "invalid"})
    void unauthenticatedProtectedRequestReturns401Json(String session) throws Exception {
        String token = switch (session) {
            case "missing" -> null;
            case "expired" -> expiredToken;
            default -> "unknown-token";
        };
        HttpResponse<String> response = request("PUT", "/api/devices/fcm-token", token, "{\"fcmToken\":\"test\"}");

        assertError(response, 401, "INVALID_SESSION");
        assertThat(exceptionProbe.lastException.get()).isNull();
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @Test
    void unhandledExceptionReallyRedispatchesToErrorAndRemains500() throws Exception {
        HttpResponse<String> response = request("GET", "/test/unhandled-error", validToken, null);

        assertThat(exceptionProbe.lastException.get()).isInstanceOf(IllegalStateException.class);
        assertThat(errorDispatchProbe.count.get()).isEqualTo(1);
        assertThat(errorDispatchProbe.originalStatus.get()).isEqualTo(500);
        assertThat(errorDispatchProbe.dispatchPath.get()).isEqualTo("/error");
        assertThat(response.statusCode()).isEqualTo(500);
    }

    @Test
    void directErrorRequestStillRequiresAuthentication() throws Exception {
        assertError(request("GET", "/error", null, null), 401, "INVALID_SESSION");
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @Test
    void validSessionCanStillUseProtectedApi() throws Exception {
        HttpResponse<String> response = request("PUT", "/api/devices/fcm-token", validToken,
                "{\"fcmToken\":\"test-fcm-" + UUID.randomUUID() + "\"}");

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    @Test
    void publicHealthEndpointStillWorksWithoutSession() throws Exception {
        assertThat(request("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void loginPostRemainsPublicButOtherMethodsRequireAuthentication() throws Exception {
        assertError(request("POST", "/api/auth/kakao", null, "{"), 400, "INVALID_REQUEST");
        assertError(request("GET", "/api/auth/kakao", null, null), 401, "INVALID_SESSION");
        assertThat(errorDispatchProbe.count.get()).isZero();
    }

    private String createSession(Instant expiresAt, Instant now) {
        UUID memberId = memberRepository.save(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), now)).getId();
        String token = UUID.randomUUID().toString();
        deviceRepository.save(Device.create(memberId, sessionTokenHasher.hash(token), expiresAt, now));
        return token;
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(MediaType.parseMediaType(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow())
                .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(objectMapper.readTree(response.body()).size()).isEqualTo(2);
        assertThat(objectMapper.readValue(response.body(), ApiErrorResponse.class))
                .isEqualTo(ApiErrorResponse.from(ErrorCode.valueOf(code)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        UnhandledExceptionController unhandledExceptionController() {
            return new UnhandledExceptionController();
        }

        @Bean
        ExceptionProbe exceptionProbe() {
            return new ExceptionProbe();
        }

        @Bean
        ErrorDispatchProbe errorDispatchProbe() {
            return new ErrorDispatchProbe();
        }

        @Bean
        FilterRegistrationBean<ErrorDispatchProbe> errorDispatchProbeRegistration(ErrorDispatchProbe probe) {
            FilterRegistrationBean<ErrorDispatchProbe> registration = new FilterRegistrationBean<>(probe);
            registration.setDispatcherTypes(DispatcherType.ERROR);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    @TestComponent
    @RestController
    static class UnhandledExceptionController {

        @GetMapping("/test/unhandled-error")
        void fail() {
            throw new IllegalStateException("Test-only unhandled failure");
        }
    }

    static class ExceptionProbe implements HandlerExceptionResolver, Ordered {

        final AtomicReference<Exception> lastException = new AtomicReference<>();

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                             Object handler, Exception exception) {
            lastException.set(exception);
            return null;
        }
    }

    static class ErrorDispatchProbe implements Filter {

        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<Integer> originalStatus = new AtomicReference<>();
        final AtomicReference<String> dispatchPath = new AtomicReference<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            count.incrementAndGet();
            originalStatus.set((Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
            dispatchPath.set(((HttpServletRequest) request).getRequestURI());
            chain.doFilter(request, response);
        }
    }
}
