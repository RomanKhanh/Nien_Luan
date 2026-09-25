package com.brainblocks.backend.service.child;

import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProfileRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Điểm kiểm tra quyền sở hữu hồ sơ trẻ duy nhất trong hệ thống.
 * Mọi thao tác nhận childProfileId từ URL đều phải lấy hồ sơ qua đây,
 * không gọi thẳng childProfileRepository.findById ở service.
 * Không tự mở transaction: luôn được gọi bên trong transaction của service gọi nó.
 */
@Component
@RequiredArgsConstructor
public class ChildProfileAccessGuard {
    private final ChildProfileRepository childProfileRepository;
    private final CurrentUserProvider currentUserProvider;

    public ChildProfile getOwnedChildProfile(Long childProfileId) {
        ChildProfile child = childProfileRepository.findById(childProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Child profile not found"));

        // customer là proxy LAZY, getId() không phát sinh thêm query
        Long ownerId = child.getCustomer().getId();
        if (!ownerId.equals(currentUserProvider.getCurrentUserId())) {
            throw new AccessDeniedException("Child profile does not belong to current user");
        }
        return child;
    }
}
