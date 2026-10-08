package com.trackflow.tms.config;

import com.trackflow.tms.util.SecurityUtils;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Fills created_at / updated_at / created_by / updated_by on every
 * {@link com.trackflow.tms.entity.AuditableEntity}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return SecurityUtils::currentUserId;
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
