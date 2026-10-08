package com.trackflow.tms.service;

import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.dto.user.ChangePasswordRequest;
import com.trackflow.tms.dto.user.CreateUserRequest;
import com.trackflow.tms.dto.user.ResetPasswordRequest;
import com.trackflow.tms.dto.user.RoleResponse;
import com.trackflow.tms.dto.user.UpdateProfileRequest;
import com.trackflow.tms.dto.user.UpdateRolesRequest;
import com.trackflow.tms.dto.user.UpdateUserRequest;
import com.trackflow.tms.dto.user.UserResponse;
import com.trackflow.tms.entity.Role;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.exception.BadRequestException;
import com.trackflow.tms.exception.ConflictException;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.mapper.UserMapper;
import com.trackflow.tms.repository.RoleRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.util.EmailUtils;
import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * User administration (admin only, enforced on the controller) and the
 * signed-in user's own profile. Admins cannot lock themselves out: they
 * cannot disable or delete their own account or drop their own ADMIN role.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int LOOKUP_LIMIT = 20;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, RoleName role, Boolean enabled, Pageable pageable) {
        return PageResponse.of(userRepository.search(blankToNull(q), role, enabled, pageable), userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userMapper.toResponse(load(id));
    }

    /** Small, active-only list for pickers (add member, assign ticket). */
    @Transactional(readOnly = true)
    public List<UserRef> lookup(String q) {
        return userRepository.search(blankToNull(q), null, true,
                        PageRequest.of(0, LOOKUP_LIMIT, Sort.by("fullName")))
                .map(userMapper::toRef)
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        Map<RoleName, Long> counts = new EnumMap<>(RoleName.class);
        for (Object[] row : userRepository.countByRole()) {
            counts.put((RoleName) row[0], (Long) row[1]);
        }
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> new RoleResponse(r.getName(), r.getDescription(), counts.getOrDefault(r.getName(), 0L)))
                .toList();
    }

    @Transactional
    public UserResponse create(AuthUser actor, CreateUserRequest request) {
        String email = EmailUtils.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setRoles(resolveRoles(request.roles()));
        userRepository.save(user);
        activity.log(actor, ActivityActions.USER_CREATED, ActivityActions.ENTITY_USER, user.getId(), null,
                "Created user " + user.getEmail() + " " + sortedNames(request.roles()), null);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(AuthUser actor, Long id, UpdateUserRequest request) {
        User user = load(id);
        String email = EmailUtils.normalize(request.email());
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        if (actor.getId().equals(id) && !request.enabled()) {
            throw new BadRequestException("You cannot disable your own account");
        }
        boolean disabling = user.isEnabled() && !request.enabled();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setEnabled(request.enabled());
        if (disabling) {
            refreshTokenService.revokeAllForUser(user.getId());
        }
        activity.log(actor, ActivityActions.USER_UPDATED, ActivityActions.ENTITY_USER, user.getId(), null,
                "Updated user " + user.getEmail() + (disabling ? " (disabled)" : ""), null);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateRoles(AuthUser actor, Long id, UpdateRolesRequest request) {
        User user = load(id);
        if (actor.getId().equals(id) && !request.roles().contains(RoleName.ADMIN)) {
            throw new BadRequestException("You cannot remove your own ADMIN role");
        }
        String before = sortedNames(user.roleNames());
        user.setRoles(resolveRoles(request.roles()));
        activity.log(actor, ActivityActions.USER_ROLES_CHANGED, ActivityActions.ENTITY_USER, user.getId(), null,
                "Roles of " + user.getEmail() + ": " + before + " -> " + sortedNames(request.roles()), null);
        return userMapper.toResponse(user);
    }

    /** Soft delete: the account disappears and can no longer sign in; its history stays. */
    @Transactional
    public void delete(AuthUser actor, Long id) {
        if (actor.getId().equals(id)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User user = load(id);
        user.setDeletedAt(clock.instant());
        user.setEnabled(false);
        refreshTokenService.revokeAllForUser(user.getId());
        activity.log(actor, ActivityActions.USER_DELETED, ActivityActions.ENTITY_USER, user.getId(), null,
                "Deleted user " + user.getEmail(), null);
    }

    /**
     * Admin reset for a forgotten password. The user is signed out everywhere
     * and should change the temporary password under Profile. Admins change
     * their own password through Profile, which asks for the current one.
     */
    @Transactional
    public void resetPassword(AuthUser actor, Long id, ResetPasswordRequest request) {
        if (actor.getId().equals(id)) {
            throw new BadRequestException("Change your own password from your Profile");
        }
        User user = load(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user.getId());
        activity.log(actor, ActivityActions.USER_PASSWORD_RESET, ActivityActions.ENTITY_USER, user.getId(), null,
                "Reset the password of " + user.getEmail(), null);
    }

    @Transactional
    public UserResponse updateProfile(AuthUser actor, UpdateProfileRequest request) {
        User user = load(actor.getId());
        user.setFullName(request.fullName().trim());
        return userMapper.toResponse(user);
    }

    /** Signs the user out everywhere: all refresh tokens are revoked. */
    @Transactional
    public void changePassword(AuthUser actor, ChangePasswordRequest request) {
        User user = load(actor.getId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private User load(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private Set<Role> resolveRoles(Set<RoleName> names) {
        Set<Role> roles = new HashSet<>();
        for (RoleName name : names) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new IllegalStateException("Role " + name + " is missing; check migrations")));
        }
        return roles;
    }

    private static String sortedNames(Set<RoleName> roles) {
        return roles.stream().sorted().map(Enum::name).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
