package com.smartpay.identity.service;

import com.smartpay.identity.entity.User;
import com.smartpay.identity.repository.UserRepository;
import com.smartpay.shared.identity.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDirectoryImpl userDirectory;

    @Test
    void findSummaryReturnsOnlyDisplayNameAndEmail() {
        User user = new User();
        user.setId(12L);
        user.setFullName("Demo Customer");
        user.setEmail("customer@smartpay.local");
        user.setPasswordHash("$2a$10$secret-hash-must-never-leak");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));

        Optional<UserSummary> summary = userDirectory.findSummary(12L);

        assertThat(summary).isPresent();
        assertThat(summary.get().id()).isEqualTo(12L);
        assertThat(summary.get().fullName()).isEqualTo("Demo Customer");
        assertThat(summary.get().email()).isEqualTo("customer@smartpay.local");
        assertThat(summary.toString()).doesNotContain("secret-hash-must-never-leak");
    }

    @Test
    void findSummaryIsEmptyForNullUserId() {
        assertThat(userDirectory.findSummary(null)).isEmpty();
    }
}
