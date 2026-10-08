package com.trackflow.tms.security;

import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads users for password login. Soft-deleted users are invisible. */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(EmailUtils.normalize(email))
                .map(AuthUser::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
