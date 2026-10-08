package com.trackflow.tms.service;

import com.trackflow.tms.config.AppProperties;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.repository.RoleRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first ADMIN in environments without demo data, from
 * BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD (a Kubernetes Secret).
 * Does nothing if they are unset or the user already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap == null || !StringUtils.hasText(bootstrap.adminEmail())
                || !StringUtils.hasText(bootstrap.adminPassword())) {
            return;
        }
        String email = EmailUtils.normalize(bootstrap.adminEmail());
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setFullName(StringUtils.hasText(bootstrap.adminName()) ? bootstrap.adminName() : "Platform Admin");
        admin.setPasswordHash(passwordEncoder.encode(bootstrap.adminPassword()));
        admin.getRoles().add(roleRepository.findByName(RoleName.ADMIN).orElseThrow());
        userRepository.save(admin);
        log.info("Bootstrap admin created: id={}", admin.getId());
    }
}
