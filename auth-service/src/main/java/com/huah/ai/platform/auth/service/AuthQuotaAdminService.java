package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.dto.RoleTokenLimitResponse;
import com.huah.ai.platform.auth.dto.RoleTokenLimitUpsertRequest;
import com.huah.ai.platform.auth.dto.UserTokenLimitResponse;
import com.huah.ai.platform.auth.dto.UserTokenLimitUpsertRequest;
import com.huah.ai.platform.auth.mapper.AiRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.AiUserTokenLimitMapper;
import com.huah.ai.platform.auth.model.AiRoleEntity;
import com.huah.ai.platform.auth.model.AiRoleTokenLimitEntity;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.model.AiUserTokenLimitEntity;
import com.huah.ai.platform.common.dto.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthQuotaAdminService {

    private static final String MESSAGE_ROLE_REQUIRED = "角色不能为空";
    private static final String MESSAGE_USER_REQUIRED = "用户不能为空";
    private static final String MESSAGE_DAILY_TOKEN_LIMIT_REQUIRED = "每日 Token 配额不能为空";
    private static final String MESSAGE_DAILY_TOKEN_LIMIT_INVALID = "每日 Token 配额不能小于 0";
    private static final String MESSAGE_ROLE_NOT_FOUND = "角色不存在";
    private static final String MESSAGE_USER_NOT_FOUND = "用户不存在";
    private static final String MESSAGE_ROLE_TOKEN_LIMIT_NOT_FOUND = "角色配额规则不存在";
    private static final String MESSAGE_USER_TOKEN_LIMIT_NOT_FOUND = "用户配额规则不存在";
    private static final String MESSAGE_ROLE_TOKEN_LIMIT_DUPLICATED = "该角色在当前助手范围下的配额规则已存在";
    private static final String MESSAGE_USER_TOKEN_LIMIT_DUPLICATED = "该用户在当前助手范围下的配额规则已存在";

    private final AiRoleMapper roleMapper;
    private final AiUserMapper userMapper;
    private final AiRoleTokenLimitMapper roleTokenLimitMapper;
    private final AiUserTokenLimitMapper userTokenLimitMapper;
    private final AuthViewAssembler authViewAssembler;

    public Result<List<RoleTokenLimitResponse>> listRoleTokenLimits() {
        return Result.ok(roleTokenLimitMapper.selectAllViews().stream()
                .map(authViewAssembler::toRoleTokenLimitResponse)
                .toList());
    }

    public Result<List<UserTokenLimitResponse>> listUserTokenLimits() {
        return Result.ok(userTokenLimitMapper.selectAllViews().stream()
                .map(authViewAssembler::toUserTokenLimitResponse)
                .toList());
    }

    @Transactional
    public Result<RoleTokenLimitResponse> createRoleTokenLimit(RoleTokenLimitUpsertRequest request) {
        Long roleId = request.getRoleId();
        Integer dailyTokenLimit = request.getDailyTokenLimit();
        if (roleId == null) {
            return Result.fail(400, MESSAGE_ROLE_REQUIRED);
        }
        if (dailyTokenLimit == null) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_REQUIRED);
        }
        if (dailyTokenLimit < 0) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_INVALID);
        }

        AiRoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            return Result.fail(404, MESSAGE_ROLE_NOT_FOUND);
        }

        String normalizedBotType = normalizeBotType(request.getBotType());
        if (roleTokenLimitMapper.selectDuplicate(roleId, normalizedBotType, null) != null) {
            return Result.fail(400, MESSAGE_ROLE_TOKEN_LIMIT_DUPLICATED);
        }

        AiRoleTokenLimitEntity entity = AiRoleTokenLimitEntity.builder()
                .roleId(roleId)
                .botType(normalizedBotType)
                .dailyTokenLimit(dailyTokenLimit)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();
        roleTokenLimitMapper.insert(entity);
        log.info("Create role token limit: roleId={}, botType={}, dailyTokenLimit={}",
                roleId, normalizedBotType, dailyTokenLimit);
        return Result.ok(authViewAssembler.toRoleTokenLimitResponse(roleTokenLimitMapper.selectViewById(entity.getId())));
    }

    @Transactional
    public Result<RoleTokenLimitResponse> updateRoleTokenLimit(Long id, RoleTokenLimitUpsertRequest request) {
        AiRoleTokenLimitEntity existing = roleTokenLimitMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, MESSAGE_ROLE_TOKEN_LIMIT_NOT_FOUND);
        }

        Long roleId = request.getRoleId() != null ? request.getRoleId() : existing.getRoleId();
        Integer dailyTokenLimit = request.getDailyTokenLimit() != null
                ? request.getDailyTokenLimit()
                : existing.getDailyTokenLimit();
        if (roleId == null) {
            return Result.fail(400, MESSAGE_ROLE_REQUIRED);
        }
        if (dailyTokenLimit == null) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_REQUIRED);
        }
        if (dailyTokenLimit < 0) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_INVALID);
        }

        AiRoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            return Result.fail(404, MESSAGE_ROLE_NOT_FOUND);
        }

        String normalizedBotType = request.getBotType() != null
                ? normalizeBotType(request.getBotType())
                : existing.getBotType();
        if (roleTokenLimitMapper.selectDuplicate(roleId, normalizedBotType, id) != null) {
            return Result.fail(400, MESSAGE_ROLE_TOKEN_LIMIT_DUPLICATED);
        }

        existing.setRoleId(roleId);
        existing.setBotType(normalizedBotType);
        existing.setDailyTokenLimit(dailyTokenLimit);
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        roleTokenLimitMapper.updateById(existing);
        log.info("Update role token limit: id={}, roleId={}, botType={}, dailyTokenLimit={}",
                id, roleId, normalizedBotType, dailyTokenLimit);
        return Result.ok(authViewAssembler.toRoleTokenLimitResponse(roleTokenLimitMapper.selectViewById(id)));
    }

    @Transactional
    public Result<Void> deleteRoleTokenLimit(Long id) {
        AiRoleTokenLimitEntity existing = roleTokenLimitMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, MESSAGE_ROLE_TOKEN_LIMIT_NOT_FOUND);
        }
        roleTokenLimitMapper.deleteById(id);
        log.info("Delete role token limit: id={}", id);
        return Result.ok();
    }

    @Transactional
    public Result<UserTokenLimitResponse> createUserTokenLimit(UserTokenLimitUpsertRequest request) {
        String userId = normalizeUserId(request.getUserId());
        Integer dailyTokenLimit = request.getDailyTokenLimit();
        if (userId == null) {
            return Result.fail(400, MESSAGE_USER_REQUIRED);
        }
        if (dailyTokenLimit == null) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_REQUIRED);
        }
        if (dailyTokenLimit < 0) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_INVALID);
        }

        AiUserEntity user = userMapper.selectByUserId(userId);
        if (user == null) {
            return Result.fail(404, MESSAGE_USER_NOT_FOUND);
        }

        String normalizedBotType = normalizeBotType(request.getBotType());
        if (userTokenLimitMapper.selectDuplicate(userId, normalizedBotType, null) != null) {
            return Result.fail(400, MESSAGE_USER_TOKEN_LIMIT_DUPLICATED);
        }

        AiUserTokenLimitEntity entity = AiUserTokenLimitEntity.builder()
                .userId(userId)
                .botType(normalizedBotType)
                .dailyTokenLimit(dailyTokenLimit)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();
        userTokenLimitMapper.insert(entity);
        log.info("Create user token limit: userId={}, botType={}, dailyTokenLimit={}",
                userId, normalizedBotType, dailyTokenLimit);
        return Result.ok(authViewAssembler.toUserTokenLimitResponse(userTokenLimitMapper.selectViewById(entity.getId())));
    }

    @Transactional
    public Result<UserTokenLimitResponse> updateUserTokenLimit(Long id, UserTokenLimitUpsertRequest request) {
        AiUserTokenLimitEntity existing = userTokenLimitMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, MESSAGE_USER_TOKEN_LIMIT_NOT_FOUND);
        }

        String userId = request.getUserId() != null ? normalizeUserId(request.getUserId()) : existing.getUserId();
        Integer dailyTokenLimit = request.getDailyTokenLimit() != null
                ? request.getDailyTokenLimit()
                : existing.getDailyTokenLimit();
        if (userId == null) {
            return Result.fail(400, MESSAGE_USER_REQUIRED);
        }
        if (dailyTokenLimit == null) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_REQUIRED);
        }
        if (dailyTokenLimit < 0) {
            return Result.fail(400, MESSAGE_DAILY_TOKEN_LIMIT_INVALID);
        }

        AiUserEntity user = userMapper.selectByUserId(userId);
        if (user == null) {
            return Result.fail(404, MESSAGE_USER_NOT_FOUND);
        }

        String normalizedBotType = request.getBotType() != null
                ? normalizeBotType(request.getBotType())
                : existing.getBotType();
        if (userTokenLimitMapper.selectDuplicate(userId, normalizedBotType, id) != null) {
            return Result.fail(400, MESSAGE_USER_TOKEN_LIMIT_DUPLICATED);
        }

        existing.setUserId(userId);
        existing.setBotType(normalizedBotType);
        existing.setDailyTokenLimit(dailyTokenLimit);
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        userTokenLimitMapper.updateById(existing);
        log.info("Update user token limit: id={}, userId={}, botType={}, dailyTokenLimit={}",
                id, userId, normalizedBotType, dailyTokenLimit);
        return Result.ok(authViewAssembler.toUserTokenLimitResponse(userTokenLimitMapper.selectViewById(id)));
    }

    @Transactional
    public Result<Void> deleteUserTokenLimit(Long id) {
        AiUserTokenLimitEntity existing = userTokenLimitMapper.selectById(id);
        if (existing == null) {
            return Result.fail(404, MESSAGE_USER_TOKEN_LIMIT_NOT_FOUND);
        }
        userTokenLimitMapper.deleteById(id);
        log.info("Delete user token limit: id={}", id);
        return Result.ok();
    }

    private String normalizeBotType(String botType) {
        if (botType == null) {
            return null;
        }
        String normalized = botType.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeUserId(String userId) {
        if (userId == null) {
            return null;
        }
        String normalized = userId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
