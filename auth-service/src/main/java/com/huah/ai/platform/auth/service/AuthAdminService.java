package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.BotPermissionResponse;
import com.huah.ai.platform.auth.dto.BotPermissionUpsertRequest;
import com.huah.ai.platform.auth.dto.UserUpsertRequest;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.model.BotPermissionEntity;
import com.huah.ai.platform.common.dto.Result;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAdminService {

    private final AiUserMapper userMapper;
    private final BotPermissionMapper botPermissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthViewAssembler authViewAssembler;

    public Result<String> initDemoUsers() {
        String[] userIds = {"admin", "rd_user", "sales_user", "hr_user", "finance_user"};
        String[] usernames = {"系统管理员", "研发工程师", "销售专员", "HR 专员", "财务专员"};
        String[] roles = {
                "ROLE_ADMIN,ROLE_RD,ROLE_SALES,ROLE_HR,ROLE_USER",
                "ROLE_RD,ROLE_USER",
                "ROLE_SALES,ROLE_USER",
                "ROLE_HR,ROLE_USER",
                "ROLE_FINANCE,ROLE_USER"
        };
        String[] departments = {"系统管理", "研发中心", "销售部", "人力资源部", "财务部"};

        for (int i = 0; i < userIds.length; i++) {
            if (userMapper.selectByUserId(userIds[i]) == null) {
                userMapper.insert(AiUserEntity.builder()
                        .userId(userIds[i])
                        .username(usernames[i])
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .roles(roles[i])
                        .department(departments[i])
                        .employeeId("EMP00" + (i + 1))
                        .build());
            }
        }
        return Result.ok("演示用户初始化完成");
    }

    public Result<List<AuthUserResponse>> listUsers() {
        return Result.ok(userMapper.selectList(null).stream().map(authViewAssembler::toUserResponse).toList());
    }

    public Result<AuthUserResponse> getUser(String id) {
        AiUserEntity user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        return Result.ok(authViewAssembler.toUserResponse(user));
    }

    public Result<AuthUserResponse> createUser(UserUpsertRequest request) {
        if (isBlank(request.getUserId()) || isBlank(request.getPassword())) {
            return Result.fail(400, "userId 和 password 不能为空");
        }
        if (userMapper.selectByUserId(request.getUserId()) != null) {
            return Result.fail(400, "userId 已存在");
        }
        if (!isBlank(request.getUsername()) && userMapper.selectByUsername(request.getUsername()) != null) {
            return Result.fail(400, "用户名已存在");
        }

        AiUserEntity user = AiUserEntity.builder()
                .userId(request.getUserId())
                .username(defaultString(request.getUsername(), request.getUserId()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .department(defaultString(request.getDepartment(), ""))
                .employeeId(defaultString(request.getEmployeeId(), ""))
                .roles(defaultString(request.getRoles(), "ROLE_USER"))
                .enabled(parseEnabled(request.getEnabled(), true))
                .build();
        userMapper.insert(user);
        log.info("Create user: userId={}", request.getUserId());
        return Result.ok(authViewAssembler.toUserResponse(user));
    }

    public Result<AuthUserResponse> updateUser(String id, UserUpsertRequest request) {
        AiUserEntity user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }

        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getRoles() != null) {
            user.setRoles(request.getRoles());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(parseEnabled(request.getEnabled(), user.isEnabled()));
        }
        if (!isBlank(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        userMapper.updateById(user);
        log.info("Update user: userId={}", id);
        return Result.ok(authViewAssembler.toUserResponse(user));
    }

    public Result<Void> deleteUser(String id) {
        AiUserEntity user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        userMapper.deleteById(id);
        log.info("Delete user: userId={}", id);
        return Result.ok();
    }

    public Result<List<BotPermissionResponse>> listPermissions() {
        return Result.ok(
                botPermissionMapper.selectList(null).stream().map(authViewAssembler::toPermissionResponse).toList());
    }

    public Result<BotPermissionResponse> getPermission(Long id) {
        BotPermissionEntity permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, "权限配置不存在");
        }
        return Result.ok(authViewAssembler.toPermissionResponse(permission));
    }

    public Result<BotPermissionResponse> createPermission(BotPermissionUpsertRequest request) {
        if (isBlank(request.getBotType())) {
            return Result.fail(400, "botType 不能为空");
        }

        BotPermissionEntity existing = botPermissionMapper.selectByBotTypeAndEnabled(request.getBotType());
        if (existing != null) {
            return Result.fail(400, "Bot 权限配置已存在");
        }

        BotPermissionEntity permission = BotPermissionEntity.builder()
                .botType(request.getBotType())
                .allowedRoles(defaultString(request.getAllowedRoles(), "ROLE_ADMIN"))
                .allowedDepartments(request.getAllowedDepartments())
                .dataScope(defaultString(request.getDataScope(), "DEPARTMENT"))
                .allowedOperations(defaultString(request.getAllowedOperations(), "READ,WRITE"))
                .dailyTokenLimit(request.getDailyTokenLimit() != null ? request.getDailyTokenLimit() : 100000)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        botPermissionMapper.insert(permission);
        log.info(
                "Create bot permission: botType={}, allowedRoles={}",
                permission.getBotType(),
                permission.getAllowedRoles());
        return Result.ok(authViewAssembler.toPermissionResponse(permission));
    }

    public Result<BotPermissionResponse> updatePermission(Long id, BotPermissionUpsertRequest request) {
        BotPermissionEntity permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, "权限配置不存在");
        }

        if (request.getAllowedRoles() != null) {
            permission.setAllowedRoles(request.getAllowedRoles());
        }
        if (request.getAllowedDepartments() != null) {
            permission.setAllowedDepartments(request.getAllowedDepartments());
        }
        if (request.getDataScope() != null) {
            permission.setDataScope(request.getDataScope());
        }
        if (request.getAllowedOperations() != null) {
            permission.setAllowedOperations(request.getAllowedOperations());
        }
        if (request.getDailyTokenLimit() != null) {
            permission.setDailyTokenLimit(request.getDailyTokenLimit());
        }
        if (request.getEnabled() != null) {
            permission.setEnabled(request.getEnabled());
        }

        botPermissionMapper.updateById(permission);
        log.info("Update bot permission: id={}, botType={}", id, permission.getBotType());
        return Result.ok(authViewAssembler.toPermissionResponse(permission));
    }

    public Result<Void> deletePermission(Long id) {
        BotPermissionEntity permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, "权限配置不存在");
        }
        botPermissionMapper.deleteById(id);
        log.info("Delete bot permission: id={}, botType={}", id, permission.getBotType());
        return Result.ok();
    }

    private Boolean parseEnabled(String enabled, Boolean defaultValue) {
        return enabled != null ? Boolean.parseBoolean(enabled) : defaultValue;
    }

    private String defaultString(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
