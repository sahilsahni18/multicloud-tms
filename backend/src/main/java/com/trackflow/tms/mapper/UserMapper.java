package com.trackflow.tms.mapper;

import com.trackflow.tms.dto.auth.AuthUserResponse;
import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.dto.project.MemberResponse;
import com.trackflow.tms.dto.user.UserResponse;
import com.trackflow.tms.entity.Role;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.security.AuthUser;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;

/** User entity / principal -> API shapes. Roles are always returned sorted (ADMIN first). */
@Mapper
public interface UserMapper {

    UserRef toRef(User user);

    UserResponse toResponse(User user);

    AuthUserResponse toAuthUser(User user);

    AuthUserResponse toAuthUser(AuthUser principal);

    MemberResponse toMember(User user, boolean owner);

    default List<RoleName> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).sorted().toList();
    }

    default List<RoleName> sortedRoles(Set<RoleName> roles) {
        return roles.stream().sorted().toList();
    }
}
