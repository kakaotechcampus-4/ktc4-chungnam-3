package com.ktc.chungnam3.remembrall.content.service;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ContentAnalysisResultService {

    private final ContentPersistenceService contentPersistenceService;

    public ContentAnalysisResultService(ContentPersistenceService contentPersistenceService) {
        this.contentPersistenceService = contentPersistenceService;
    }

    public boolean applyOutcome(UUID contentId, AnalysisOutcome outcome) {
        return contentPersistenceService.applyOutcome(contentId, outcome);
    }
}
