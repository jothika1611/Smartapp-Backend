package com.smartpay.identity.service;

import com.smartpay.identity.repository.UserRepository;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.shared.identity.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/** Identity-module implementation of the shared lookup contract used by payment. */
@Service
public class UserDirectoryImpl implements UserDirectory {

    private final UserRepository userRepository;

    public UserDirectoryImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userExists(Long userId) {
        return userId != null && userRepository.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(user -> user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findSummary(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .map(user -> new UserSummary(user.getId(), user.getFullName(), user.getEmail()));
    }
}
