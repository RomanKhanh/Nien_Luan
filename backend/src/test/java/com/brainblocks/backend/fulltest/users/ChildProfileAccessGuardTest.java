package com.brainblocks.backend.fulltest.users;

import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProfileRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildProfileAccessGuardTest {
    @Mock ChildProfileRepository children;
    @Mock CurrentUserProvider currentUser;
    @InjectMocks ChildProfileAccessGuard guard;

    @Test
    void ownerReceivesTheRequestedChild() {
        ChildProfile child = ChildProfile.builder().id(10L).customer(Customer.builder().id(1L).build()).build();
        when(children.findById(10L)).thenReturn(Optional.of(child));
        when(currentUser.getCurrentUserId()).thenReturn(1L);
        assertThat(guard.getOwnedChildProfile(10L)).isSameAs(child);
    }

    @Test
    void foreignChildHasSameErrorAsMissingChild() {
        ChildProfile child = ChildProfile.builder().id(10L).customer(Customer.builder().id(1L).build()).build();
        when(children.findById(10L)).thenReturn(Optional.of(child));
        when(children.findById(99L)).thenReturn(Optional.empty());
        when(currentUser.getCurrentUserId()).thenReturn(2L);
        assertThatThrownBy(() -> guard.getOwnedChildProfile(10L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Child profile not found");
        assertThatThrownBy(() -> guard.getOwnedChildProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Child profile not found");
    }
}
