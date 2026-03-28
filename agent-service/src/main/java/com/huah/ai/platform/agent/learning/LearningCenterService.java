package com.huah.ai.platform.agent.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplatePayload;
import com.huah.ai.platform.agent.learning.dto.LearningFavoritePayload;
import com.huah.ai.platform.agent.learning.dto.LearningNotePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningCenterService {

    private static final int FAVORITE_LIMIT = 300;
    private static final int NOTE_LIMIT = 300;
    private static final int TEMPLATE_LIMIT = 20;

    private final LearningFavoriteMapper favoriteMapper;
    private final LearningNoteMapper noteMapper;
    private final FollowUpTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    public List<LearningFavoritePayload> listFavorites(String userId) {
        return favoriteMapper.selectByUserId(userId).stream()
                .limit(FAVORITE_LIMIT)
                .map(this::toFavoritePayload)
                .toList();
    }

    public void saveFavorite(String userId, LearningFavoritePayload payload) {
        LearningFavoriteRecord record = LearningFavoriteRecord.builder()
                .id(payload.getId())
                .userId(userId)
                .responseId(payload.getResponseId())
                .role(payload.getRole())
                .content(payload.getContent())
                .agentType(payload.getAgentType())
                .sessionId(payload.getSessionId())
                .sessionSummary(payload.getSessionSummary())
                .sourceMessageIndex(payload.getSourceMessageIndex())
                .createdAt(payload.getCreatedAt())
                .lastCollectedAt(payload.getLastCollectedAt())
                .duplicateCount(payload.getDuplicateCount())
                .tagsJson(writeJson(payload.getTags()))
                .sessionConfigSnapshotJson(writeJson(payload.getSessionConfigSnapshot()))
                .build();
        upsertFavorite(record);
        trimFavorites(userId);
    }

    public void deleteFavorite(String userId, String id) {
        favoriteMapper.deleteByUserIdAndId(userId, id);
    }

    public List<LearningNotePayload> listNotes(String userId) {
        return noteMapper.selectByUserId(userId).stream()
                .limit(NOTE_LIMIT)
                .map(this::toNotePayload)
                .toList();
    }

    public void saveNote(String userId, LearningNotePayload payload) {
        LearningNoteRecord record = LearningNoteRecord.builder()
                .id(payload.getId())
                .userId(userId)
                .title(payload.getTitle())
                .content(payload.getContent())
                .sourceType(payload.getSourceType())
                .relatedFavoriteId(payload.getRelatedFavoriteId())
                .relatedSessionId(payload.getRelatedSessionId())
                .relatedAgentType(payload.getRelatedAgentType())
                .relatedSessionSummary(payload.getRelatedSessionSummary())
                .relatedMessageIndex(payload.getRelatedMessageIndex())
                .tagsJson(writeJson(payload.getTags()))
                .createdAt(payload.getCreatedAt())
                .updatedAt(payload.getUpdatedAt())
                .build();
        upsertNote(record);
        trimNotes(userId);
    }

    public void deleteNote(String userId, String id) {
        noteMapper.deleteByUserIdAndId(userId, id);
    }

    public List<FollowUpTemplatePayload> listTemplates(String userId) {
        return templateMapper.selectByUserId(userId).stream()
                .limit(TEMPLATE_LIMIT)
                .map(this::toTemplatePayload)
                .toList();
    }

    public void saveTemplate(String userId, FollowUpTemplatePayload payload) {
        FollowUpTemplateRecord record = FollowUpTemplateRecord.builder()
                .id(payload.getId())
                .userId(userId)
                .name(payload.getName())
                .content(payload.getContent())
                .sourceCount(payload.getSourceCount())
                .updatedAt(payload.getUpdatedAt())
                .build();
        upsertTemplate(record);
        trimTemplates(userId);
    }

    public void deleteTemplate(String userId, String id) {
        templateMapper.deleteByUserIdAndId(userId, id);
    }

    private void upsertFavorite(LearningFavoriteRecord record) {
        LearningFavoriteRecord existing = favoriteMapper.selectOne(new QueryWrapper<LearningFavoriteRecord>()
                .eq("user_id", record.getUserId())
                .eq("id", record.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            favoriteMapper.insert(record);
            return;
        }
        record.setUserId(existing.getUserId());
        favoriteMapper.updateById(record);
    }

    private void upsertNote(LearningNoteRecord record) {
        LearningNoteRecord existing = noteMapper.selectOne(new QueryWrapper<LearningNoteRecord>()
                .eq("user_id", record.getUserId())
                .eq("id", record.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            noteMapper.insert(record);
            return;
        }
        record.setUserId(existing.getUserId());
        noteMapper.updateById(record);
    }

    private void upsertTemplate(FollowUpTemplateRecord record) {
        FollowUpTemplateRecord existing = templateMapper.selectOne(new QueryWrapper<FollowUpTemplateRecord>()
                .eq("user_id", record.getUserId())
                .eq("id", record.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            templateMapper.insert(record);
            return;
        }
        record.setUserId(existing.getUserId());
        templateMapper.updateById(record);
    }

    private void trimFavorites(String userId) {
        List<LearningFavoriteRecord> records = favoriteMapper.selectByUserId(userId);
        records.stream().skip(FAVORITE_LIMIT).forEach(item -> favoriteMapper.deleteById(item.getId()));
    }

    private void trimNotes(String userId) {
        List<LearningNoteRecord> records = noteMapper.selectByUserId(userId);
        records.stream().skip(NOTE_LIMIT).forEach(item -> noteMapper.deleteById(item.getId()));
    }

    private void trimTemplates(String userId) {
        List<FollowUpTemplateRecord> records = templateMapper.selectByUserId(userId);
        records.stream().skip(TEMPLATE_LIMIT).forEach(item -> templateMapper.deleteById(item.getId()));
    }

    private LearningFavoritePayload toFavoritePayload(LearningFavoriteRecord record) {
        LearningFavoritePayload payload = new LearningFavoritePayload();
        payload.setId(record.getId());
        payload.setResponseId(record.getResponseId());
        payload.setRole(record.getRole());
        payload.setContent(record.getContent());
        payload.setAgentType(record.getAgentType());
        payload.setSessionId(record.getSessionId());
        payload.setSessionSummary(record.getSessionSummary());
        payload.setSourceMessageIndex(record.getSourceMessageIndex());
        payload.setCreatedAt(record.getCreatedAt());
        payload.setLastCollectedAt(record.getLastCollectedAt());
        payload.setDuplicateCount(record.getDuplicateCount());
        payload.setTags(readList(record.getTagsJson()));
        payload.setSessionConfigSnapshot(readSessionConfig(record.getSessionConfigSnapshotJson()));
        return payload;
    }

    private LearningNotePayload toNotePayload(LearningNoteRecord record) {
        LearningNotePayload payload = new LearningNotePayload();
        payload.setId(record.getId());
        payload.setTitle(record.getTitle());
        payload.setContent(record.getContent());
        payload.setSourceType(record.getSourceType());
        payload.setRelatedFavoriteId(record.getRelatedFavoriteId());
        payload.setRelatedSessionId(record.getRelatedSessionId());
        payload.setRelatedAgentType(record.getRelatedAgentType());
        payload.setRelatedSessionSummary(record.getRelatedSessionSummary());
        payload.setRelatedMessageIndex(record.getRelatedMessageIndex());
        payload.setTags(readList(record.getTagsJson()));
        payload.setCreatedAt(record.getCreatedAt());
        payload.setUpdatedAt(record.getUpdatedAt());
        return payload;
    }

    private FollowUpTemplatePayload toTemplatePayload(FollowUpTemplateRecord record) {
        FollowUpTemplatePayload payload = new FollowUpTemplatePayload();
        payload.setId(record.getId());
        payload.setName(record.getName());
        payload.setContent(record.getContent());
        payload.setSourceCount(record.getSourceCount());
        payload.setUpdatedAt(record.getUpdatedAt());
        return payload;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("serialize learning payload failed: {}", e.getMessage());
            return null;
        }
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("deserialize learning tags failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SessionConfigRequest readSessionConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, SessionConfigRequest.class);
        } catch (Exception e) {
            log.warn("deserialize session config snapshot failed: {}", e.getMessage());
            return null;
        }
    }
}
