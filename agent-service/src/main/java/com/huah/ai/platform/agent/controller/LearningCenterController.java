package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.learning.LearningCenterService;
import com.huah.ai.platform.agent.learning.dto.FollowUpTemplatePayload;
import com.huah.ai.platform.agent.learning.dto.LearningFavoritePayload;
import com.huah.ai.platform.agent.learning.dto.LearningNotePayload;
import com.huah.ai.platform.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/learning")
@RequiredArgsConstructor
public class LearningCenterController {

    private static final String MESSAGE_SAVED = "saved";
    private static final String MESSAGE_DELETED = "deleted";

    private final LearningCenterService learningCenterService;
    private final AgentRequestContextResolver requestContextResolver;

    @GetMapping("/favorites")
    public Result<List<LearningFavoritePayload>> listFavorites(HttpServletRequest request) {
        return Result.ok(learningCenterService.listFavorites(currentUserId(request)));
    }

    @PostMapping("/favorites")
    public Result<String> saveFavorite(@RequestBody LearningFavoritePayload payload, HttpServletRequest request) {
        learningCenterService.saveFavorite(currentUserId(request), payload);
        return Result.ok(MESSAGE_SAVED);
    }

    @DeleteMapping("/favorites/{id}")
    public Result<String> deleteFavorite(@PathVariable("id") String id, HttpServletRequest request) {
        learningCenterService.deleteFavorite(currentUserId(request), id);
        return Result.ok(MESSAGE_DELETED);
    }

    @GetMapping("/notes")
    public Result<List<LearningNotePayload>> listNotes(HttpServletRequest request) {
        return Result.ok(learningCenterService.listNotes(currentUserId(request)));
    }

    @PostMapping("/notes")
    public Result<String> saveNote(@RequestBody LearningNotePayload payload, HttpServletRequest request) {
        learningCenterService.saveNote(currentUserId(request), payload);
        return Result.ok(MESSAGE_SAVED);
    }

    @DeleteMapping("/notes/{id}")
    public Result<String> deleteNote(@PathVariable("id") String id, HttpServletRequest request) {
        learningCenterService.deleteNote(currentUserId(request), id);
        return Result.ok(MESSAGE_DELETED);
    }

    @GetMapping("/templates")
    public Result<List<FollowUpTemplatePayload>> listTemplates(HttpServletRequest request) {
        return Result.ok(learningCenterService.listTemplates(currentUserId(request)));
    }

    @PostMapping("/templates")
    public Result<String> saveTemplate(@RequestBody FollowUpTemplatePayload payload, HttpServletRequest request) {
        learningCenterService.saveTemplate(currentUserId(request), payload);
        return Result.ok(MESSAGE_SAVED);
    }

    @DeleteMapping("/templates/{id}")
    public Result<String> deleteTemplate(@PathVariable("id") String id, HttpServletRequest request) {
        learningCenterService.deleteTemplate(currentUserId(request), id);
        return Result.ok(MESSAGE_DELETED);
    }

    private String currentUserId(HttpServletRequest request) {
        return requestContextResolver.resolve(request).getUserId();
    }
}
