package com.brainblocks.backend.service.user;

import com.brainblocks.backend.dto.request.ChangePasswordRequest;
import com.brainblocks.backend.dto.request.UpdateProfileRequest;
import com.brainblocks.backend.dto.response.UserProfileResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        return toProfileResponse(getCurrentUserEntity());
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = getCurrentUserEntity();
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));

        // defaultAddress chỉ có ở Customer; tài khoản Admin bỏ qua field này
        if (user instanceof Customer customer) {
            customer.setDefaultAddress(blankToNull(request.defaultAddress()));
        }
        return toProfileResponse(user);
    }

    @Transactional
    public void changeMyPassword(ChangePasswordRequest request) {
        User user = getCurrentUserEntity();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    // dùng chung cho cả AdminUserService để 2 phía trả cùng một định dạng
    public UserProfileResponse toProfileResponse(User user) {
        String defaultAddress = user instanceof Customer customer ? customer.getDefaultAddress() : null;
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.isEnabled(),
                defaultAddress,
                user.getCreatedAt()
        );
    }

    // CustomUserDetails giữ entity đã detached từ lúc lọc JWT, nên phải load lại để JPA theo dõi thay đổi
    private User getCurrentUserEntity() {
        Long userId = currentUserProvider.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
