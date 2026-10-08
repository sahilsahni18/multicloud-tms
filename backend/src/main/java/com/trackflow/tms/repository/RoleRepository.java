package com.trackflow.tms.repository;

import com.trackflow.tms.entity.Role;
import com.trackflow.tms.entity.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByName(RoleName name);
}
