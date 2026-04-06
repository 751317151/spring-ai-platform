package com.huah.ai.platform.agent.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huah.ai.platform.agent.dto.SessionConfigRequest;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplateRequest;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplateResponse;
import com.huah.ai.platform.agent.learning.dto.LearningFavoriteRequest;
import com.huah.ai.platform.agent.learning.dto.LearningFavoriteResponse;
import com.huah.ai.platform.agent.learning.dto.LearningNoteRequest;
import com.huah.ai.platform.agent.learning.dto.LearningNoteResponse;
import com.huah.ai.platform.common.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public List<LearningFavoriteResponse> listFavorites(String userId) {
        return favoriteMapper.selectByUserId(userId).stream()
                .limit(FAVORITE_LIMIT)
                .map(this::toFavoriteResponse)
                .toList();
    }

    public void saveFavorite(String userId, LearningFavoriteRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LearningFavoriteEntity entity = LearningFavoriteEntity.builder()
                .id(request.getId())
                .userId(userId)
                .responseId(request.getResponseId())
                .role(request.getRole())
                .content(request.getContent())
                .agentType(request.getAgentType())
                .sessionId(request.getSessionId())
                .sessionSummary(request.getSessionSummary())
                .sourceMessageIndex(request.getSourceMessageIndex())
                .createdAt(now)
                .updatedAt(now)
                .lastCollectedAt(request.getLastCollectedAt() != null ? request.getLastCollectedAt() : now)
                .duplicateCount(request.getDuplicateCount())
                .tagsJson(writeJson(request.getTags()))
                .sessionConfigSnapshotJson(writeJson(request.getSessionConfigSnapshot()))
                .build();
        upsertFavorite(entity);
        trimFavorites(userId);
    }

    public void deleteFavorite(String userId, String id) {
        favoriteMapper.deleteByUserIdAndId(userId, parseRequiredLong(id));
    }

    public List<LearningNoteResponse> listNotes(String userId) {
        return noteMapper.selectByUserId(userId).stream()
                .limit(NOTE_LIMIT)
                .map(this::toNoteResponse)
                .toList();
    }

    public void saveNote(String userId, LearningNoteRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LearningNoteEntity entity = LearningNoteEntity.builder()
                .id(request.getId())
                .userId(userId)
                .title(request.getTitle())
                .content(request.getContent())
                .sourceType(request.getSourceType())
                .relatedFavoriteId(request.getRelatedFavoriteId())
                .relatedSessionId(request.getRelatedSessionId())
                .relatedAgentType(request.getRelatedAgentType())
                .relatedSessionSummary(request.getRelatedSessionSummary())
                .relatedMessageIndex(request.getRelatedMessageIndex())
                .tagsJson(writeJson(request.getTags()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        upsertNote(entity);
        trimNotes(userId);
    }

    public void deleteNote(String userId, String id) {
        noteMapper.deleteByUserIdAndId(userId, parseRequiredLong(id));
    }

    public List<FollowUpTemplateResponse> listTemplates(String userId) {
        return templateMapper.selectByUserId(userId).stream()
                .limit(TEMPLATE_LIMIT)
                .map(this::toTemplateResponse)
                .toList();
    }

    public void saveTemplate(String userId, FollowUpTemplateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        FollowUpTemplateEntity entity = FollowUpTemplateEntity.builder()
                .id(request.getId())
                .userId(userId)
                .name(request.getName())
                .content(request.getContent())
                .sourceCount(request.getSourceCount())
                .createdAt(now)
                .updatedAt(now)
                .build();
        upsertTemplate(entity);
        trimTemplates(userId);
    }

    public void deleteTemplate(String userId, String id) {
        templateMapper.deleteByUserIdAndId(userId, parseRequiredLong(id));
    }

    private void upsertFavorite(LearningFavoriteEntity entity) {
        ensureId(entity);
        LearningFavoriteEntity existing = favoriteMapper.selectOne(new QueryWrapper<LearningFavoriteEntity>()
                .eq("user_id", entity.getUserId())
                .eq("id", entity.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            favoriteMapper.insert(entity);
            return;
        }
        entity.setUserId(existing.getUserId());
        entity.setCreatedAt(existing.getCreatedAt());
        favoriteMapper.updateById(entity);
    }

    private void upsertNote(LearningNoteEntity entity) {
        ensureId(entity);
        LearningNoteEntity existing = noteMapper.selectOne(new QueryWrapper<LearningNoteEntity>()
                .eq("user_id", entity.getUserId())
                .eq("id", entity.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            noteMapper.insert(entity);
            return;
        }
        entity.setUserId(existing.getUserId());
        entity.setCreatedAt(existing.getCreatedAt());
        noteMapper.updateById(entity);
    }

    private void upsertTemplate(FollowUpTemplateEntity entity) {
        ensureId(entity);
        FollowUpTemplateEntity existing = templateMapper.selectOne(new QueryWrapper<FollowUpTemplateEntity>()
                .eq("user_id", entity.getUserId())
                .eq("id", entity.getId())
                .last("LIMIT 1"));
        if (existing == null) {
            templateMapper.insert(entity);
            return;
        }
        entity.setUserId(existing.getUserId());
        entity.setCreatedAt(existing.getCreatedAt());
        templateMapper.updateById(entity);
    }

    private void trimFavorites(String userId) {
        List<LearningFavoriteEntity> records = favoriteMapper.selectByUserId(userId);
        records.stream().skip(FAVORITE_LIMIT).forEach(item -> favoriteMapper.deleteById(item.getId()));
    }

    private void trimNotes(String userId) {
        List<LearningNoteEntity> records = noteMapper.selectByUserId(userId);
        records.stream().skip(NOTE_LIMIT).forEach(item -> noteMapper.deleteById(item.getId()));
    }

    private void trimTemplates(String userId) {
        List<FollowUpTemplateEntity> records = templateMapper.selectByUserId(userId);
        records.stream().skip(TEMPLATE_LIMIT).forEach(item -> templateMapper.deleteById(item.getId()));
    }

    private LearningFavoriteResponse toFavoriteResponse(LearningFavoriteEntity entity) {
        LearningFavoriteResponse response = new LearningFavoriteResponse();
        response.setId(entity.getId());
        response.setResponseId(entity.getResponseId());
        response.setRole(entity.getRole());
        response.setContent(entity.getContent());
        response.setAgentType(entity.getAgentType());
        response.setSessionId(entity.getSessionId());
        response.setSessionSummary(entity.getSessionSummary());
        response.setSourceMessageIndex(entity.getSourceMessageIndex());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setLastCollectedAt(entity.getLastCollectedAt());
        response.setDuplicateCount(entity.getDuplicateCount());
        response.setTags(readList(entity.getTagsJson()));
        response.setSessionConfigSnapshot(readSessionConfig(entity.getSessionConfigSnapshotJson()));
        return response;
    }

    private LearningNoteResponse toNoteResponse(LearningNoteEntity entity) {
        LearningNoteResponse response = new LearningNoteResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setContent(entity.getContent());
        response.setSourceType(entity.getSourceType());
        response.setRelatedFavoriteId(entity.getRelatedFavoriteId());
        response.setRelatedSessionId(entity.getRelatedSessionId());
        response.setRelatedAgentType(entity.getRelatedAgentType());
        response.setRelatedSessionSummary(entity.getRelatedSessionSummary());
        response.setRelatedMessageIndex(entity.getRelatedMessageIndex());
        response.setTags(readList(entity.getTagsJson()));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private FollowUpTemplateResponse toTemplateResponse(FollowUpTemplateEntity entity) {
        FollowUpTemplateResponse response = new FollowUpTemplateResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setContent(entity.getContent());
        response.setSourceCount(entity.getSourceCount());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
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

    private void ensureId(LearningFavoriteEntity entity) {
        if (entity.getId() == null) {
            entity.setId(snowflakeIdGenerator.nextLongId());
        }
    }

    private void ensureId(LearningNoteEntity entity) {
        if (entity.getId() == null) {
            entity.setId(snowflakeIdGenerator.nextLongId());
        }
    }

    private void ensureId(FollowUpTemplateEntity entity) {
        if (entity.getId() == null) {
            entity.setId(snowflakeIdGenerator.nextLongId());
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

    private Long parseRequiredLong(String value) {
        return Long.parseLong(value);
    }

}
