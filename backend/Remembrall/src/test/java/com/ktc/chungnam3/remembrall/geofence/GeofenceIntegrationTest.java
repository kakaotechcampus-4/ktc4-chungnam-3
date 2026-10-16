package com.ktc.chungnam3.remembrall.geofence;

import com.ktc.chungnam3.remembrall.auth.security.ApiAuthenticationEntryPoint;
import com.ktc.chungnam3.remembrall.auth.security.SecurityConfig;
import com.ktc.chungnam3.remembrall.auth.security.SessionAuthenticationFilter;
import com.ktc.chungnam3.remembrall.auth.token.SessionTokenHasher;
import com.ktc.chungnam3.remembrall.common.exception.ApiErrorResponse;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.common.exception.GlobalExceptionHandler;
import com.ktc.chungnam3.remembrall.common.geo.GeoDistance;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.contentplace.ContentPlace;
import com.ktc.chungnam3.remembrall.domain.device.Device;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.memberconsent.MemberConsent;
import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.trigger.Trigger;
import com.ktc.chungnam3.remembrall.geofence.config.GeofenceConfiguration;
import com.ktc.chungnam3.remembrall.geofence.config.GeofenceProperties;
import com.ktc.chungnam3.remembrall.geofence.controller.GeofenceController;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceQuery.Platform;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceResponse;
import com.ktc.chungnam3.remembrall.geofence.dto.GeofenceResponse.PlaceGeofence;
import com.ktc.chungnam3.remembrall.geofence.service.GeofenceService;
import com.ktc.chungnam3.remembrall.repository.DeviceRepository;
import com.ktc.chungnam3.remembrall.repository.MemberConsentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@Testcontainers
@SpringBootTest(classes = GeofenceIntegrationTest.GeofenceTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.google.genai.api-key=test-api-key", "spring.ai.google.genai.embedding.api-key=test-api-key"
})
class GeofenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private static final double LATITUDE = 37;
    private static final double LONGITUDE = 127;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MemberRepository members;
    @Autowired private DeviceRepository devices;
    @Autowired private SessionTokenHasher hasher;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private GeofenceProperties properties;
    @LocalServerPort private int port;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private UUID memberId;
    private String token;

    @BeforeEach
    void setUp() {
        reset(properties);
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM content");
        jdbc.update("DELETE FROM place");
        memberId = member();
        token = UUID.randomUUID().toString();
        devices.save(Device.create(memberId, hasher.hash(token), NOW.plusSeconds(3600), NOW));
        consent(memberId, "LOCATION_BASED_SERVICE");
    }

    @Test
    void configuredDefaultsReserveOnePlatformSlot() {
        assertThat(properties.iosLimit()).isEqualTo(20);
        assertThat(properties.androidLimit()).isEqualTo(100);
        assertThat(properties.placeRadiusMeters()).isEqualTo(150);
        assertThat(properties.resyncMarginMeters()).isEqualTo(500);
        assertThat(properties.resyncMinRadiusMeters()).isEqualTo(3000);
    }

    @ParameterizedTest
    @EnumSource(Platform.class)
    void returnsNearestPlacesWithinPlatformLimit(Platform platform) throws Exception {
        List<UUID> expected = new ArrayList<>();
        for (int index = 110; index >= 1; index--) {
            expected.add(eligiblePlace(memberId, LATITUDE + index * 0.001, LONGITUDE));
        }
        Collections.reverse(expected);

        var response = list(platform);

        int limit = platform == Platform.IOS ? 19 : 99;
        assertThat(response.regions()).isEmpty();
        assertThat(response.places()).extracting(PlaceGeofence::placeId).containsExactlyElementsOf(expected.subList(0, limit));
        assertThat(response.places()).allSatisfy(place -> {
            assertThat(place.radiusMeters()).isEqualTo(150);
            assertThat(place.longitude()).isEqualTo(LONGITUDE);
            assertThat(place.geofenceId()).isEqualTo("place:" + place.placeId());
        });
        for (int index = 0; index < limit; index++) {
            assertThat(response.places().get(index).latitude()).isEqualTo(LATITUDE + (index + 1) * 0.001);
        }
    }

    @Test
    void onlyOwnTriggersAreIncludedAndIdsRemainStableAcrossRequests() throws Exception {
        UUID own = eligiblePlace(memberId, LATITUDE + 0.001, LONGITUDE);
        UUID other = eligiblePlace(member(), LATITUDE, LONGITUDE);
        savedContent(memberId, other, "SUCCESS", "AVAILABLE");

        var first = list(Platform.IOS);
        var second = body(request("latitude=37.01&longitude=127&platform=ANDROID", token));

        assertThat(first.places()).extracting(PlaceGeofence::placeId).containsExactly(own);
        assertThat(second.places()).isEqualTo(first.places());
        assertThat(first.places().getFirst().geofenceId()).isEqualTo("place:" + own);
    }

    @Test
    void equalDistancesAreSortedByPlaceIdAndMultipleSavesDoNotDuplicatePlaces() throws Exception {
        var ids = List.of(UUID.fromString("80000000-0000-0000-0000-000000000000"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        for (UUID id : ids) {
            place(id, LATITUDE, LONGITUDE);
            savedContent(memberId, id, "SUCCESS", "AVAILABLE");
            savedContent(memberId, id, "PARTIAL", "UNKNOWN");
            trigger(memberId, id);
        }

        var expected = ids.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        assertThat(list(Platform.IOS).places()).extracting(PlaceGeofence::placeId).containsExactlyElementsOf(expected);
        assertThat(list(Platform.IOS).places()).extracting(PlaceGeofence::placeId).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @CsvSource({"FAILED,AVAILABLE", "FAILED,UNKNOWN", "FAILED,UNAVAILABLE", "SUCCESS,UNAVAILABLE", "PARTIAL,UNAVAILABLE"})
    void excludesBadOwnContentEvenWhenAnotherMemberHasGoodContentAndKeepsAnyEligibleOwnContent(
            String analysis, String source) throws Exception {
        UUID place = place(LATITUDE, LONGITUDE);
        trigger(memberId, place);
        savedContent(memberId, place, analysis, source);
        savedContent(member(), place, "SUCCESS", "AVAILABLE");
        assertEmpty(list(Platform.IOS));

        UUID healthy = savedContent(memberId, place, "SUCCESS", "AVAILABLE");
        assertThat(list(Platform.IOS).places()).extracting(PlaceGeofence::placeId).containsExactly(place);
        jdbc.update("UPDATE content SET analysis_status = 'PARTIAL', source_status = 'UNKNOWN' WHERE id = ?", healthy);
        assertThat(list(Platform.IOS).places()).extracting(PlaceGeofence::placeId).containsExactly(place);
    }

    @Test
    void excludesMixedInvalidContentAndTriggersWithNoEligibleOwnSave() throws Exception {
        UUID invalid = place(LATITUDE, LONGITUDE);
        trigger(memberId, invalid);
        savedContent(memberId, invalid, "FAILED", "AVAILABLE");
        savedContent(memberId, invalid, "SUCCESS", "UNAVAILABLE");
        UUID onlyOtherSaves = place(LATITUDE, LONGITUDE);
        trigger(memberId, onlyOtherSaves);
        savedContent(member(), onlyOtherSaves, "SUCCESS", "AVAILABLE");
        trigger(memberId, place(LATITUDE, LONGITUDE));
        savedContent(memberId, place(LATITUDE, LONGITUDE), "SUCCESS", "AVAILABLE");

        assertEmpty(list(Platform.ANDROID));
    }

    @Test
    void resyncUsesRequestCenterAndFarthestReturnedPlaceRatherThanAnOmittedPlace() throws Exception {
        doReturn(3).when(properties).iosLimit();
        eligiblePlace(memberId, LATITUDE + 0.01, LONGITUDE);
        UUID farthestSelected = eligiblePlace(memberId, LATITUDE + 0.04, LONGITUDE);
        eligiblePlace(memberId, LATITUDE + 0.4, LONGITUDE);

        var response = list(Platform.IOS);

        assertThat(response.places()).hasSize(2);
        assertThat(response.places().getLast().placeId()).isEqualTo(farthestSelected);
        assertThat(response.resync()).isNotNull();
        assertThat(response.resync().geofenceId()).isEqualTo("resync");
        assertThat(response.resync().latitude()).isEqualTo(LATITUDE);
        assertThat(response.resync().longitude()).isEqualTo(LONGITUDE);
        double distance = GeoDistance.meters(LATITUDE, LONGITUDE, LATITUDE + 0.04, LONGITUDE);
        assertThat(response.resync().radiusMeters()).isCloseTo(distance + 500, offset(0.000001));
    }

    @Test
    void resyncRadiusDoesNotFallBelowConfiguredMinimum() throws Exception {
        eligiblePlace(memberId, LATITUDE + 0.001, LONGITUDE);

        assertThat(list(Platform.IOS).resync().radiusMeters()).isEqualTo(3000);
    }

    @Test
    void configuredLimitsAndRadiiAreUsedForBothPlatforms() throws Exception {
        doReturn(4).when(properties).iosLimit();
        doReturn(6).when(properties).androidLimit();
        doReturn(275).when(properties).placeRadiusMeters();
        doReturn(700.0).when(properties).resyncMarginMeters();
        doReturn(50.0).when(properties).resyncMinRadiusMeters();
        for (int index = 1; index <= 8; index++) {
            eligiblePlace(memberId, LATITUDE + index * 0.001, LONGITUDE);
        }

        var ios = list(Platform.IOS);
        var android = list(Platform.ANDROID);

        assertThat(ios.places()).hasSize(3).allMatch(place -> place.radiusMeters() == 275);
        assertThat(android.places()).hasSize(5).allMatch(place -> place.radiusMeters() == 275);
        assertThat(ios.resync().radiusMeters())
                .isCloseTo(GeoDistance.meters(LATITUDE, LONGITUDE, LATITUDE + 0.003, LONGITUDE) + 700, offset(0.000001));
        assertThat(android.resync().radiusMeters())
                .isCloseTo(GeoDistance.meters(LATITUDE, LONGITUDE, LATITUDE + 0.005, LONGITUDE) + 700, offset(0.000001));
    }

    @Test
    void noPlacesOrOnlyAResyncSlotReturnsNullResyncAndEmptyArrays() throws Exception {
        var response = request("latitude=37&longitude=127&platform=IOS", token);
        assertEmpty(body(response));
        var json = mapper.readTree(response.body());
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("regions", "resync", "places");
        assertThat(json.get("resync").isNull()).isTrue();

        eligiblePlace(memberId, LATITUDE, LONGITUDE);
        doReturn(1).when(properties).iosLimit();
        assertEmpty(list(Platform.IOS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "WITHDRAWN", "PUBLIC_ONLY", "OTHER_MEMBER"})
    void locationConsentMustBeActiveAndBelongToRequestingMember(String state) throws Exception {
        eligiblePlace(memberId, LATITUDE, LONGITUDE);
        jdbc.update("DELETE FROM member_consent");
        if (state.equals("WITHDRAWN")) {
            consent(memberId, "LOCATION_BASED_SERVICE");
            jdbc.update("UPDATE member_consent SET withdrawn_at = ?", Timestamp.from(NOW));
        } else if (state.equals("PUBLIC_ONLY")) {
            consent(memberId, "PUBLIC_CANDIDATE_CONTRIBUTION");
        } else if (state.equals("OTHER_MEMBER")) {
            consent(member(), "LOCATION_BASED_SERVICE");
        }

        assertEmpty(list(Platform.IOS));
        assertEmpty(list(Platform.ANDROID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "latitude=90.001&longitude=127&platform=IOS", "latitude=-90.001&longitude=127&platform=IOS",
            "latitude=37&longitude=180.001&platform=ANDROID", "latitude=37&longitude=-180.001&platform=ANDROID",
            "latitude=NaN&longitude=127&platform=IOS", "latitude=Infinity&longitude=127&platform=IOS",
            "latitude=-Infinity&longitude=127&platform=IOS", "latitude=37&longitude=NaN&platform=IOS",
            "latitude=37&longitude=Infinity&platform=IOS", "latitude=37&longitude=-Infinity&platform=IOS",
            "latitude=abc&longitude=127&platform=IOS", "latitude=37&longitude=abc&platform=IOS",
            "longitude=127&platform=IOS", "latitude=37&platform=IOS", "latitude=37&longitude=127",
            "latitude=&longitude=127&platform=IOS", "latitude=37&longitude=&platform=IOS",
            "latitude=37&longitude=127&platform=", "latitude=37&longitude=127&platform=WEB",
            "latitude=37&longitude=127&platform=ios", "latitude=37&longitude=127&platform=android"
    })
    void invalidParametersReturn400WithExistingErrorFormat(String query) throws Exception {
        assertError(request(query, token), 400, ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @CsvSource({"90,180", "-90,-180", "0,0"})
    void coordinateBoundariesAreAllowed(double latitude, double longitude) throws Exception {
        assertEmpty(body(request("latitude=" + latitude + "&longitude=" + longitude + "&platform=IOS", token)));
    }

    @Test
    void missingAndExpiredSessionsReturn401() throws Exception {
        assertError(request("latitude=37&longitude=127&platform=IOS", null), 401, ErrorCode.INVALID_SESSION);
        jdbc.update("UPDATE device SET session_expires_at = ? WHERE member_id = ?", Timestamp.from(NOW), memberId);
        assertError(request("latitude=37&longitude=127&platform=IOS", token), 401, ErrorCode.INVALID_SESSION);
    }

    private GeofenceResponse list(Platform platform) throws Exception {
        return body(request("latitude=" + LATITUDE + "&longitude=" + LONGITUDE + "&platform=" + platform, token));
    }

    private GeofenceResponse body(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readValue(response.body(), GeofenceResponse.class);
    }

    private void assertEmpty(GeofenceResponse response) {
        assertThat(response.regions()).isEmpty();
        assertThat(response.places()).isEmpty();
        assertThat(response.resync()).isNull();
    }

    private void assertError(HttpResponse<String> response, int status, ErrorCode code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(mapper.readValue(response.body(), ApiErrorResponse.class)).isEqualTo(ApiErrorResponse.from(code));
    }

    private HttpResponse<String> request(String query, String session) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/geofences?" + query))
                .timeout(Duration.ofSeconds(20)).GET();
        if (session != null) {
            request.header("Authorization", "Bearer " + session);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID member() {
        return members.save(Member.create(AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
    }

    private void consent(UUID memberId, String type) {
        jdbc.update("""
                INSERT INTO member_consent (id, member_id, consent_type, terms_version, agreed_at)
                VALUES (?, ?, ?, 'v1', ?)
                """, UUID.randomUUID(), memberId, type, Timestamp.from(NOW));
    }

    private UUID eligiblePlace(UUID owner, double latitude, double longitude) {
        UUID placeId = place(latitude, longitude);
        savedContent(owner, placeId, "SUCCESS", "AVAILABLE");
        trigger(owner, placeId);
        return placeId;
    }

    private UUID place(double latitude, double longitude) {
        UUID id = UUID.randomUUID();
        place(id, latitude, longitude);
        return id;
    }

    private void place(UUID id, double latitude, double longitude) {
        jdbc.update("""
                INSERT INTO place (id, name, latitude, longitude, geocoding_provider, geocoding_place_id,
                                   verification_provider, verification_place_id, verified_at)
                VALUES (?, ?, ?, ?, 'LOCATIONIQ', ?, 'KAKAO', ?, ?)
                """, id, "Place " + id, latitude, longitude, id.toString(), id.toString(), Timestamp.from(NOW));
    }

    private UUID savedContent(UUID owner, UUID placeId, String analysis, String source) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO content (id, video_id, analysis_status, source_status) VALUES (?, ?, ?, ?)",
                id, id.toString().substring(0, 32), analysis, source);
        jdbc.update("INSERT INTO personal_save (id, member_id, content_id, saved_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), owner, id, Timestamp.from(NOW));
        jdbc.update("INSERT INTO content_place (id, content_id, place_id) VALUES (?, ?, ?)", UUID.randomUUID(), id, placeId);
        return id;
    }

    private void trigger(UUID owner, UUID placeId) {
        jdbc.update("INSERT INTO trigger (id, member_id, place_id) VALUES (?, ?, ?)", UUID.randomUUID(), owner, placeId);
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Member.class, Device.class, MemberConsent.class, Content.class,
            ContentPlace.class, PersonalSave.class, Place.class, Trigger.class})
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class, includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {MemberRepository.class, DeviceRepository.class, MemberConsentRepository.class, TriggerRepository.class}))
    @Import({SecurityConfig.class, SessionAuthenticationFilter.class, ApiAuthenticationEntryPoint.class,
            SessionTokenHasher.class, GlobalExceptionHandler.class, GeofenceConfiguration.class,
            GeofenceController.class, GeofenceService.class})
    static class GeofenceTestApplication {
        @Bean
        Clock geofenceTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
