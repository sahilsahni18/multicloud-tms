package com.trackflow.tms.controller;

import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.dto.user.ChangePasswordRequest;
import com.trackflow.tms.dto.user.CreateUserRequest;
import com.trackflow.tms.dto.user.ResetPasswordRequest;
import com.trackflow.tms.dto.user.UpdateProfileRequest;
import com.trackflow.tms.dto.user.UpdateRolesRequest;
import com.trackflow.tms.dto.user.UpdateUserRequest;
import com.trackflow.tms.dto.user.UserResponse;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.UserService;
import com.trackflow.tms.util.SortGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User administration (ADMIN) and the signed-in user's profile")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users; filter by text, role and enabled flag")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) Boolean enabled,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return userService.search(q, role, enabled,
                SortGuard.require(pageable, Set.of("createdAt", "email", "fullName", "lastLoginAt")));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Up to 20 active users matching the text, for member / assignee pickers")
    public List<UserRef> lookup(@RequestParam(required = false) String q) {
        return userService.lookup(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a user")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a user with roles")
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal AuthUser actor,
                                               @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(actor, request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update email, name, enabled (disabling signs the user out)")
    public UserResponse update(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(actor, id, request);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a user's roles")
    public UserResponse updateRoles(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(actor, id, request);
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set a new password for a user who forgot theirs (signs them out everywhere)")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(actor, id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete (deactivate) a user; their tickets and history are kept")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id) {
        userService.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update my name")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthUser actor,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(actor, request);
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change my password (signs me out on every device)")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser actor,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(actor, request);
        return ResponseEntity.noContent().build();
    }
}
