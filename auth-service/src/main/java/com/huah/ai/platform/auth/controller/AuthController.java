package com.huah.ai.platform.auth.controller;

import com.huah.ai.platform.auth.dto.AuthUserResponse;
import com.huah.ai.platform.auth.dto.BotPermissionResponse;
import com.huah.ai.platform.auth.dto.BotPermissionUpsertRequest;
import com.huah.ai.platform.auth.dto.LoginRequest;
import com.huah.ai.platform.auth.dto.LogoutRequest;
import com.huah.ai.platform.auth.dto.RefreshTokenRequest;
import com.huah.ai.platform.auth.dto.TokenResponse;
import com.huah.ai.platform.auth.dto.TokenValidationResponse;
import com.huah.ai.platform.auth.dto.UserUpsertRequest;
import com.huah.ai.platform.auth.service.AuthAdminService;
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

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<List<BotPermissionResponse>> listPermissions() {
        return authAdminService.listPermissions();
    }

    @GetMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermissionResponse> getPermission(@PathVariable(name = "id") Long id) {
        return authAdminService.getPermission(id);
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermissionResponse> createPermission(@RequestBody BotPermissionUpsertRequest request) {
        return authAdminService.createPermission(request);
    }

    @PutMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<BotPermissionResponse> updatePermission(
            @PathVariable(name = "id") Long id,
            @RequestBody BotPermissionUpsertRequest request) {
        return authAdminService.updatePermission(id, request);
    }

    @DeleteMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Void> deletePermission(@PathVariable(name = "id") Long id) {
        return authAdminService.deletePermission(id);
    }
}
