package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.BotPermissionResponse;
import com.huah.ai.platform.auth.dto.LoginRequest;
import com.huah.ai.platform.auth.dto.LogoutRequest;
import com.huah.ai.platform.auth.dto.RefreshTokenRequest;
import com.huah.ai.platform.auth.dto.RoleOptionResponse;
import com.huah.ai.platform.auth.dto.RoleTokenLimitResponse;
import com.huah.ai.platform.auth.dto.RoleTokenLimitUpsertRequest;
import com.huah.ai.platform.auth.dto.RoleUsageResponse;
import com.huah.ai.platform.auth.dto.RoleUpsertRequest;
import com.huah.ai.platform.auth.dto.TokenResponse;
import com.huah.ai.platform.auth.dto.TokenValidationResponse;
import com.huah.ai.platform.auth.dto.UserTokenLimitResponse;
import com.huah.ai.platform.auth.dto.UserTokenLimitUpsertRequest;
import com.huah.ai.platform.auth.dto.UserUpsertRequest;
import com.huah.ai.platform.auth.service.AuthAdminService;
import com.huah.ai.platform.auth.service.AuthQuotaAdminService;
import com.huah.ai.platform.auth.service.AuthTokenService;
import com.huah.ai.platform.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthTokenService authTokenService;
    private final AuthAdminService authAdminService;
    private final AuthQuotaAdminService authQuotaAdminService;

    @PostMapping("/login")
    public Result<TokenResponse> login(@RequestBody LoginRequest request) {
        return authTokenService.login(request);
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @RequestBody(required = false) LogoutRequest request) {
        return authTokenService.logout(authorization, request);
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return authTokenService.refresh(request);
    }

    @GetMapping("/validate")
    public Result<TokenValidationResponse> validate(
            @RequestHeader(value = "Authorization", defaultValue = "") String authorization) {
        return authTokenService.validate(authorization);
    }

    @GetMapping("/my-bots")
    public Result<List<BotPermissionResponse>> myBots(HttpServletRequest request, Authentication authentication) {
        return authTokenService.myBots(request, authentication);
    }

    @PostMapping("/init-demo-users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<String> initDemoUsers() {
        return authAdminService.initDemoUsers();
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<AuthUserResponse>> listUsers() {
        return authAdminService.listUsers();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<RoleOptionResponse>> listRoles() {
        return authAdminService.listRoles();
    }

    @GetMapping("/roles/{id}/usage")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<RoleUsageResponse> getRoleUsage(@PathVariable(name = "id") Long id) {
        return authAdminService.getRoleUsage(id);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<RoleOptionResponse> createRole(@RequestBody RoleUpsertRequest request) {
        return authAdminService.createRole(request);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<RoleOptionResponse> updateRole(
            @PathVariable(name = "id") Long id,
            @RequestBody RoleUpsertRequest request) {
        return authAdminService.updateRole(id, request);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteRole(@PathVariable(name = "id") Long id) {
        return authAdminService.deleteRole(id);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AuthUserResponse> getUser(@PathVariable(name = "id") String id) {
        return authAdminService.getUser(id);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AuthUserResponse> createUser(@RequestBody UserUpsertRequest request) {
        return authAdminService.createUser(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<AuthUserResponse> updateUser(@PathVariable(name = "id") String id, @RequestBody UserUpsertRequest request) {
        return authAdminService.updateUser(id, request);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteUser(@PathVariable(name = "id") String id) {
        return authAdminService.deleteUser(id);
    }

    @GetMapping("/role-token-limits")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<RoleTokenLimitResponse>> listRoleTokenLimits() {
        return authQuotaAdminService.listRoleTokenLimits();
    }

    @PostMapping("/role-token-limits")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<RoleTokenLimitResponse> createRoleTokenLimit(@RequestBody RoleTokenLimitUpsertRequest request) {
        return authQuotaAdminService.createRoleTokenLimit(request);
    }

    @PutMapping("/role-token-limits/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<RoleTokenLimitResponse> updateRoleTokenLimit(
            @PathVariable(name = "id") Long id,
            @RequestBody RoleTokenLimitUpsertRequest request) {
        return authQuotaAdminService.updateRoleTokenLimit(id, request);
    }

    @DeleteMapping("/role-token-limits/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteRoleTokenLimit(@PathVariable(name = "id") Long id) {
        return authQuotaAdminService.deleteRoleTokenLimit(id);
    }

    @GetMapping("/user-token-limits")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<UserTokenLimitResponse>> listUserTokenLimits() {
        return authQuotaAdminService.listUserTokenLimits();
    }

    @PostMapping("/user-token-limits")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<UserTokenLimitResponse> createUserTokenLimit(@RequestBody UserTokenLimitUpsertRequest request) {
        return authQuotaAdminService.createUserTokenLimit(request);
    }

    @PutMapping("/user-token-limits/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<UserTokenLimitResponse> updateUserTokenLimit(
            @PathVariable(name = "id") Long id,
            @RequestBody UserTokenLimitUpsertRequest request) {
        return authQuotaAdminService.updateUserTokenLimit(id, request);
    }

    @DeleteMapping("/user-token-limits/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deleteUserTokenLimit(@PathVariable(name = "id") Long id) {
        return authQuotaAdminService.deleteUserTokenLimit(id);
    }
}
