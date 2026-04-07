package com.huah.ai.platform.auth.service;

import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.BotPermissionResponse;
import com.huah.ai.platform.auth.dto.BotPermissionUpsertRequest;
import com.huah.ai.platform.auth.dto.RoleOptionResponse;
import com.huah.ai.platform.auth.dto.RoleUsageResponse;
import com.huah.ai.platform.auth.dto.RoleUpsertRequest;
import com.huah.ai.platform.auth.dto.UserUpsertRequest;
import com.huah.ai.platform.auth.mapper.AiBotPermissionRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleMapper;
import com.huah.ai.platform.auth.mapper.AiRoleTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.AiUserMapper;
import com.huah.ai.platform.auth.mapper.AiUserRoleMapper;
import com.huah.ai.platform.auth.mapper.AiUserTokenLimitMapper;
import com.huah.ai.platform.auth.mapper.BotPermissionMapper;
import com.huah.ai.platform.auth.model.AiRoleEntity;
import com.huah.ai.platform.auth.model.AiUserEntity;
import com.huah.ai.platform.auth.model.BotPermissionEntity;
import com.huah.ai.platform.common.dto.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAdminService {

    private static final String MESSAGE_INIT_USERS_DONE = "\u6f14\u793a\u7528\u6237\u521d\u59cb\u5316\u5b8c\u6210";
    private static final String MESSAGE_USER_NOT_FOUND = "\u7528\u6237\u4e0d\u5b58\u5728";
    private static final String MESSAGE_USERNAME_EXISTS = "\u7528\u6237\u540d\u5df2\u5b58\u5728";
    private static final String MESSAGE_USERID_EXISTS = "userId \u5df2\u5b58\u5728";
    private static final String MESSAGE_USERID_PASSWORD_REQUIRED = "userId \u548c password \u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MESSAGE_PERMISSION_NOT_FOUND = "\u6743\u9650\u914d\u7f6e\u4e0d\u5b58\u5728";
    private static final String MESSAGE_BOT_TYPE_REQUIRED = "botType \u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MESSAGE_BOT_PERMISSION_EXISTS = "Bot \u6743\u9650\u914d\u7f6e\u5df2\u5b58\u5728";
    private static final String MESSAGE_ROLE_NOT_FOUND = "\u89d2\u8272\u4e0d\u5b58\u5728";
    private static final String MESSAGE_ROLE_NAME_REQUIRED = "roleName \u4e0d\u80fd\u4e3a\u7a7a";
    private static final String MESSAGE_ROLE_NAME_INVALID = "roleName \u5fc5\u987b\u4ee5 ROLE_ \u5f00\u5934\uff0c\u4e14\u53ea\u80fd\u5305\u542b\u5927\u5199\u5b57\u6bcd\u3001\u6570\u5b57\u548c\u4e0b\u5212\u7ebf";
    private static final String MESSAGE_ROLE_NAME_EXISTS = "roleName \u5df2\u5b58\u5728";
    private static final String MESSAGE_ROLE_REFERENCED = "\u89d2\u8272\u4ecd\u88ab\u7528\u6237\u6216 AI \u52a9\u624b\u6743\u9650\u89c4\u5219\u5f15\u7528\uff0c\u65e0\u6cd5\u5220\u9664";

    private final AiRoleMapper roleMapper;
    private final AiUserMapper userMapper;
    private final AiUserRoleMapper userRoleMapper;
    private final AiUserTokenLimitMapper userTokenLimitMapper;
    private final BotPermissionMapper botPermissionMapper;
    private final AiBotPermissionRoleMapper botPermissionRoleMapper;
    private final AiRoleTokenLimitMapper roleTokenLimitMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthViewAssembler authViewAssembler;
    private final AuthRoleService authRoleService;

    @Transactional
    public Result<String> initDemoUsers() {
        String[] userIds = {"admin", "rd_user", "sales_user", "hr_user", "finance_user"};
        String[] usernames = {
                "\u7ba1\u7406\u5458",
                "\u7814\u53d1\u5de5\u7a0b\u5e08",
                "\u9500\u552e\u4e13\u5458",
                "HR \u4e13\u5458",
                "\u8d22\u52a1\u4e13\u5458"
        };
        String[] roles = {
                "ROLE_ADMIN,ROLE_RD,ROLE_SALES,ROLE_HR,ROLE_FINANCE,ROLE_USER",
                "ROLE_RD,ROLE_USER",
                "ROLE_SALES,ROLE_USER",
                "ROLE_HR,ROLE_USER",
                "ROLE_FINANCE,ROLE_USER"
        };
        String[] departments = {
                "\u7cfb\u7edf\u7ba1\u7406",
                "\u7814\u53d1\u4e2d\u5fc3",
                "\u9500\u552e\u90e8",
                "\u4eba\u529b\u8d44\u6e90\u90e8",
                "\u8d22\u52a1\u90e8"
        };
        String[] provinces = {"北京", "上海", "广东", "浙江", "四川"};
        String[] cities = {"北京", "上海", "深圳", "杭州", "成都"};

        for (int i = 0; i < userIds.length; i++) {
            if (userMapper.selectByUserId(userIds[i]) == null) {
                AiUserEntity user = AiUserEntity.builder()
                        .userId(userIds[i])
                        .username(usernames[i])
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .department(departments[i])
                        .province(provinces[i])
                        .city(cities[i])
                        .employeeId("EMP00" + (i + 1))
                        .enabled(true)
                        .build();
                userMapper.insert(user);
                authRoleService.replaceUserRoles(userIds[i], roles[i]);
            }
        }
        return Result.ok(MESSAGE_INIT_USERS_DONE);
    }

    public Result<List<AuthUserResponse>> listUsers() {
        return Result.ok(userMapper.selectAllViews().stream()
                .map(authViewAssembler::toUserResponse)
                .toList());
    }

    public Result<List<RoleOptionResponse>> listRoles() {
        return Result.ok(roleMapper.selectList(null).stream()
                .sorted((left, right) -> left.getRoleName().compareToIgnoreCase(right.getRoleName()))
                .map(authViewAssembler::toRoleResponse)
                .toList());
    }

    public Result<RoleUsageResponse> getRoleUsage(Long id) {
        AiRoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            return Result.fail(404, MESSAGE_ROLE_NOT_FOUND);
        }

        int userCount = userRoleMapper.countUsersByRoleId(id);
        int permissionCount = botPermissionRoleMapper.countPermissionsByRoleId(id);

        return Result.ok(RoleUsageResponse.builder()
                .roleId(role.getId())
                .roleName(role.getRoleName())
                .userCount(userCount)
                .permissionCount(permissionCount)
                .userReferences(userRoleMapper.selectUserReferencesByRoleId(id))
                .permissionReferences(botPermissionRoleMapper.selectPermissionReferencesByRoleId(id))
                .build());
    }

    @Transactional
    public Result<RoleOptionResponse> createRole(RoleUpsertRequest request) {
        String normalizedRoleName = normalizeRoleName(request.getRoleName());
        if (normalizedRoleName == null) {
            return Result.fail(400, MESSAGE_ROLE_NAME_REQUIRED);
        }
        if (!isValidRoleName(normalizedRoleName)) {
            return Result.fail(400, MESSAGE_ROLE_NAME_INVALID);
        }
        if (findRoleByName(normalizedRoleName) != null) {
            return Result.fail(400, MESSAGE_ROLE_NAME_EXISTS);
        }

        AiRoleEntity role = AiRoleEntity.builder()
                .roleName(normalizedRoleName)
                .description(defaultString(request.getDescription(), ""))
                .build();
        roleMapper.insert(role);
        log.info("Create role: roleName={}", role.getRoleName());
        return Result.ok(authViewAssembler.toRoleResponse(role));
    }

    @Transactional
    public Result<RoleOptionResponse> updateRole(Long id, RoleUpsertRequest request) {
        AiRoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            return Result.fail(404, MESSAGE_ROLE_NOT_FOUND);
        }

        String normalizedRoleName = normalizeRoleName(request.getRoleName());
        if (normalizedRoleName == null) {
            return Result.fail(400, MESSAGE_ROLE_NAME_REQUIRED);
        }
        if (!isValidRoleName(normalizedRoleName)) {
            return Result.fail(400, MESSAGE_ROLE_NAME_INVALID);
        }

        AiRoleEntity existingRole = findRoleByName(normalizedRoleName);
        if (existingRole != null && !id.equals(existingRole.getId())) {
            return Result.fail(400, MESSAGE_ROLE_NAME_EXISTS);
        }

        role.setRoleName(normalizedRoleName);
        role.setDescription(defaultString(request.getDescription(), ""));
        roleMapper.updateById(role);
        log.info("Update role: id={}, roleName={}", id, role.getRoleName());
        return Result.ok(authViewAssembler.toRoleResponse(role));
    }

    @Transactional
    public Result<Void> deleteRole(Long id) {
        AiRoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            return Result.fail(404, MESSAGE_ROLE_NOT_FOUND);
        }
        if (userRoleMapper.countUsersByRoleId(id) > 0 || botPermissionRoleMapper.countPermissionsByRoleId(id) > 0) {
            return Result.fail(400, MESSAGE_ROLE_REFERENCED);
        }
        roleTokenLimitMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
        log.info("Delete role: id={}, roleName={}", id, role.getRoleName());
        return Result.ok();
    }

    public Result<AuthUserResponse> getUser(String id) {
        AiUserEntity user = userMapper.selectViewById(id);
        if (user == null) {
            return Result.fail(404, MESSAGE_USER_NOT_FOUND);
        }
        return Result.ok(authViewAssembler.toUserResponse(user));
    }

    @Transactional
    public Result<AuthUserResponse> createUser(UserUpsertRequest request) {
        if (isBlank(request.getUserId()) || isBlank(request.getPassword())) {
            return Result.fail(400, MESSAGE_USERID_PASSWORD_REQUIRED);
        }
        if (userMapper.selectByUserId(request.getUserId()) != null) {
            return Result.fail(400, MESSAGE_USERID_EXISTS);
        }
        if (!isBlank(request.getUsername())) {
            AiUserEntity existedUser = userMapper.selectByUsername(request.getUsername());
            if (existedUser != null) {
                return Result.fail(400, MESSAGE_USERNAME_EXISTS);
            }
        }

        AiUserEntity user = AiUserEntity.builder()
                .userId(request.getUserId())
                .username(defaultString(request.getUsername(), request.getUserId()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .department(defaultString(request.getDepartment(), ""))
                .province(defaultString(request.getProvince(), ""))
                .city(defaultString(request.getCity(), ""))
                .employeeId(defaultString(request.getEmployeeId(), ""))
                .enabled(parseEnabled(request.getEnabled(), true))
                .build();

        try {
            authRoleService.validateRolesOrThrow(request.getRoles());
            userMapper.insert(user);
            authRoleService.replaceUserRoles(user.getUserId(), request.getRoles());
        } catch (IllegalArgumentException exception) {
            return Result.fail(400, exception.getMessage());
        }

        log.info("Create user: userId={}", request.getUserId());
        return Result.ok(authViewAssembler.toUserResponse(userMapper.selectViewById(user.getUserId())));
    }

    @Transactional
    public Result<AuthUserResponse> updateUser(String id, UserUpsertRequest request) {
        AiUserEntity user = userMapper.selectByUserId(id);
        if (user == null) {
            return Result.fail(404, MESSAGE_USER_NOT_FOUND);
        }

        if (request.getUsername() != null) {
            AiUserEntity existedUser = userMapper.selectByUsername(request.getUsername());
            if (existedUser != null && !id.equals(existedUser.getUserId())) {
                return Result.fail(400, MESSAGE_USERNAME_EXISTS);
            }
            user.setUsername(request.getUsername());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getProvince() != null) {
            user.setProvince(request.getProvince());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        if (request.getEmployeeId() != null) {
            user.setEmployeeId(request.getEmployeeId());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(parseEnabled(request.getEnabled(), user.isEnabled()));
        }
        if (!isBlank(request.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        try {
            if (request.getRoles() != null) {
                authRoleService.validateRolesOrThrow(request.getRoles());
            }
            userMapper.updateById(user);
            if (request.getRoles() != null) {
                authRoleService.replaceUserRoles(id, request.getRoles());
            }
        } catch (IllegalArgumentException exception) {
            return Result.fail(400, exception.getMessage());
        }

        log.info("Update user: userId={}", id);
        return Result.ok(authViewAssembler.toUserResponse(userMapper.selectViewById(id)));
    }

    @Transactional
    public Result<Void> deleteUser(String id) {
        AiUserEntity user = userMapper.selectByUserId(id);
        if (user == null) {
            return Result.fail(404, MESSAGE_USER_NOT_FOUND);
        }
        userRoleMapper.deleteByUserId(id);
        userTokenLimitMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        log.info("Delete user: userId={}", id);
        return Result.ok();
    }

    public Result<List<BotPermissionResponse>> listPermissions() {
        return Result.ok(botPermissionMapper.selectList(null).stream()
                .peek(this::syncPermissionRolesView)
                .map(authViewAssembler::toPermissionResponse)
                .toList());
    }

    public Result<BotPermissionResponse> getPermission(Long id) {
        BotPermissionEntity permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, MESSAGE_PERMISSION_NOT_FOUND);
        }
        syncPermissionRolesView(permission);
        return Result.ok(authViewAssembler.toPermissionResponse(permission));
    }

    @Transactional
    public Result<BotPermissionResponse> createPermission(BotPermissionUpsertRequest request) {
        if (isBlank(request.getBotType())) {
            return Result.fail(400, MESSAGE_BOT_TYPE_REQUIRED);
        }

        BotPermissionEntity existing = botPermissionMapper.selectByBotTypeAndEnabled(request.getBotType());
        if (existing != null) {
            return Result.fail(400, MESSAGE_BOT_PERMISSION_EXISTS);
        }

        BotPermissionEntity permission = BotPermissionEntity.builder()
                .botType(request.getBotType())
                .allowedRoles(defaultString(request.getAllowedRoles(), "ROLE_ADMIN"))
                .allowedDepartments(request.getAllowedDepartments())
                .dataScope(defaultString(request.getDataScope(), "DEPARTMENT"))
                .allowedOperations(defaultString(request.getAllowedOperations(), "READ,WRITE"))
                .dailyTokenLimit(request.getDailyTokenLimit() != null ? request.getDailyTokenLimit() : 100000)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        try {
            authRoleService.validatePermissionRolesOrThrow(permission.getAllowedRoles());
            botPermissionMapper.insert(permission);
            authRoleService.replacePermissionRoles(permission.getId(), permission.getAllowedRoles());
        } catch (IllegalArgumentException exception) {
            return Result.fail(400, exception.getMessage());
        }

        syncPermissionRolesView(permission);
        log.info(
                "Create bot permission: botType={}, allowedRoles={}",
                permission.getBotType(),
                permission.getAllowedRoles());
        return Result.ok(authViewAssembler.toPermissionResponse(permission));
    }

    @Transactional
    public Result<BotPermissionResponse> updatePermission(Long id, BotPermissionUpsertRequest request) {
        BotPermissionEntity permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, MESSAGE_PERMISSION_NOT_FOUND);
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

        try {
            authRoleService.validatePermissionRolesOrThrow(permission.getAllowedRoles());
            botPermissionMapper.updateById(permission);
            authRoleService.replacePermissionRoles(permission.getId(), permission.getAllowedRoles());
        } catch (IllegalArgumentException exception) {
            return Result.fail(400, exception.getMessage());
        }

        syncPermissionRolesView(permission);
        log.info("Update bot permission: id={}, botType={}", id, permission.getBotType());
        return Result.ok(authViewAssembler.toPermissionResponse(permission));
    }

    @Transactional
    public Result<Void> deletePermission(Long id) {
        BotPermissionEntity permission = botPermissionMapper.selectById(id);
        if (permission == null) {
            return Result.fail(404, MESSAGE_PERMISSION_NOT_FOUND);
        }
        authRoleService.replacePermissionRoles(id, null);
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

    private void syncPermissionRolesView(BotPermissionEntity permission) {
        permission.setAllowedRoles(authRoleService.getPermissionRoleNamesCsv(permission.getId(), permission.getAllowedRoles()));
    }

    private AiRoleEntity findRoleByName(String roleName) {
        return roleMapper.selectOne(new LambdaQueryWrapper<AiRoleEntity>()
                .eq(AiRoleEntity::getRoleName, roleName)
                .last("LIMIT 1"));
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        return roleName.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isValidRoleName(String roleName) {
        return roleName.matches("^ROLE_[A-Z0-9_]+$");
    }
}
