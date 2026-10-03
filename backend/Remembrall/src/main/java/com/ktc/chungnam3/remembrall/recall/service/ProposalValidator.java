package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.domain.recallexecution.ProposalType;
import com.ktc.chungnam3.remembrall.recall.agent.ProposalSource;
import com.ktc.chungnam3.remembrall.recall.agent.RecallAgentResult;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.UUID;

@Component
public class ProposalValidator {
    private final ContentRepository contents;
    private final PlaceRepository places;
    private final ContentPlaceRepository links;
    private final PersonalSaveRepository saves;

    public ProposalValidator(ContentRepository contents, PlaceRepository places,
                             ContentPlaceRepository links, PersonalSaveRepository saves) {
        this.contents = contents;
        this.places = places;
        this.links = links;
        this.saves = saves;
    }

    public boolean valid(UUID memberId, RecallAgentResult result) {
        if (result.proposalType() == null || result.proposalPayload() == null
                || result.proposalPayload().items() == null || result.decisionSummary() == null
                || result.title() == null || result.body() == null) {
            return false;
        }
        var items = result.proposalPayload().items();
        if (result.proposalType() == ProposalType.COURSE ? items.size() < 2 : items.size() != 1) {
            return false;
        }
        var coursePlaceIds = new HashSet<UUID>();
        for (var item : items) {
            if (item == null || item.contentId() == null || item.source() != ProposalSource.PERSONAL
                    || !contents.existsById(item.contentId())
                    || saves.findByMemberIdAndContentId(memberId, item.contentId()).isEmpty()) {
                return false;
            }
            if (result.proposalType() == ProposalType.CONTENT) {
                if (item.placeId() != null) {
                    return false;
                }
            } else if (item.placeId() == null || !places.existsById(item.placeId())
                    || links.findByContent_IdAndPlace_Id(item.contentId(), item.placeId()).isEmpty()) {
                return false;
            }
            if (result.proposalType() == ProposalType.COURSE && !coursePlaceIds.add(item.placeId())) {
                return false;
            }
        }
        return true;
    }
}
