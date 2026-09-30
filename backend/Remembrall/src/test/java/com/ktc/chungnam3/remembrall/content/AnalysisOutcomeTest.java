package com.ktc.chungnam3.remembrall.content;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisOutcomeTest {

    @Test
    void successRequiresNoFailureCodeAndAllowsNoPlaces() {
        assertThatCode(() -> outcome(ContentAnalysisStatus.SUCCESS, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> outcome(
                ContentAnalysisStatus.SUCCESS,
                ContentAnalysisFailureCode.EXTRACTION_API_ERROR
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot have a failure code");
    }

    @Test
    void partialAndFailedRequireStableFailureCode() {
        assertThatThrownBy(() -> outcome(ContentAnalysisStatus.PARTIAL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a failure code");
        assertThatThrownBy(() -> outcome(ContentAnalysisStatus.FAILED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a failure code");
    }

    @Test
    void videoUnavailableRequiresUnavailableSourceStatus() {
        assertVideoUnavailableRejected(null);
        assertVideoUnavailableRejected(ContentSourceStatus.UNKNOWN);
        assertVideoUnavailableRejected(ContentSourceStatus.AVAILABLE);
        assertThatCode(() -> outcome(
                ContentAnalysisStatus.FAILED,
                ContentAnalysisFailureCode.VIDEO_UNAVAILABLE,
                ContentSourceStatus.UNAVAILABLE
        )).doesNotThrowAnyException();
    }

    private void assertVideoUnavailableRejected(ContentSourceStatus sourceStatus) {
        assertThatThrownBy(() -> outcome(
                ContentAnalysisStatus.FAILED,
                ContentAnalysisFailureCode.VIDEO_UNAVAILABLE,
                sourceStatus
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires sourceStatus UNAVAILABLE");
    }

    private AnalysisOutcome outcome(
            ContentAnalysisStatus status,
            ContentAnalysisFailureCode failureCode
    ) {
        return outcome(status, failureCode, null);
    }

    private AnalysisOutcome outcome(
            ContentAnalysisStatus status,
            ContentAnalysisFailureCode failureCode,
            ContentSourceStatus sourceStatus
    ) {
        return new AnalysisOutcome(
                status,
                failureCode,
                sourceStatus,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
