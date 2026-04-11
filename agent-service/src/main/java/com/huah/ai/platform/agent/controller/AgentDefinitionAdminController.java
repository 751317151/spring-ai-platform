package com.huah.ai.platform.agent.controller;

import com.huah.ai.platform.agent.dto.AgentDefinitionResponse;
import com.huah.ai.platform.agent.dto.AgentDefinitionUpsertRequest;
import com.huah.ai.platform.agent.dto.AssistantToolCatalogItemResponse;
import com.huah.ai.platform.agent.service.AgentDefinitionService;
import com.huah.ai.platform.agent.service.AssistantCapabilityResolverService;
import com.huah.ai.platform.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AgentApiConstants.BASE_PATH + "/definitions")
@RequiredArgsConstructor
public class AgentDefinitionAdminController {

    private final AgentDefinitionService agentDefinitionService;
    private final AssistantCapabilityResolverService assistantCapabilityResolverService;
    private final AgentControllerSupport controllerSupport;

    @GetMapping("/tool-catalog")
    public Result<List<AssistantToolCatalogItemResponse>> listToolCatalog(HttpServletRequest request) {
        Result<List<AssistantToolCatalogItemResponse>> deny = requireAdmin(request);
        if (deny != null) {
            return deny;
        }
        return Result.ok(assistantCapabilityResolverService.listToolCatalog());
    }

    @GetMapping
    public Result<List<AgentDefinitionResponse>> list(HttpServletRequest request) {
        Result<List<AgentDefinitionResponse>> deny = requireAdmin(request);
        if (deny != null) {
            return deny;
        }
        return Result.ok(agentDefinitionService.listAll());
    }

    @GetMapping("/{agentCode}")
    public Result<AgentDefinitionResponse> get(
            @PathVariable("agentCode") String agentCode,
            HttpServletRequest request) {
        Result<AgentDefinitionResponse> deny = requireAdmin(request);
        if (deny != null) {
            return deny;
        }
        return Result.ok(agentDefinitionService.getRequired(agentCode));
    }

    @PostMapping
    public Result<AgentDefinitionResponse> create(
            @RequestBody AgentDefinitionUpsertRequest body,
            HttpServletRequest request) {
        Result<AgentDefinitionResponse> deny = requireAdmin(request);
        if (deny != null) {
            return deny;
        }
        return Result.ok(agentDefinitionService.create(controllerSupport.currentUserId(request), body));
    }

    @PutMapping("/{agentCode}")
    public Result<AgentDefinitionResponse> update(
            @PathVariable("agentCode") String agentCode,
            @RequestBody AgentDefinitionUpsertRequest body,
            HttpServletRequest request) {
        Result<AgentDefinitionResponse> deny = requireAdmin(request);
        if (deny != null) {
            return deny;
        }
        return Result.ok(agentDefinitionService.update(agentCode, controllerSupport.currentUserId(request), body));
    }

    @DeleteMapping("/{agentCode}")
    public Result<String> delete(
            @PathVariable("agentCode") String agentCode,
            HttpServletRequest request) {
        Result<String> deny = requireAdmin(request);
        if (deny != null) {
            return deny;
        }
        agentDefinitionService.delete(agentCode);
        return Result.ok("ok");
    }

    private <T> Result<T> requireAdmin(HttpServletRequest request) {
        if (!controllerSupport.isAdmin(request)) {
            return Result.fail(AgentApiConstants.HTTP_FORBIDDEN, "Only admin can manage assistant definitions");
        }
        return null;
    }
}
