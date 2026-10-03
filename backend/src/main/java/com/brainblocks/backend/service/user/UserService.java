package com.brainblocks.backend.service.user;

import com.brainblocks.backend.dto.request.user.ChangePasswordRequest;
import com.brainblocks.backend.dto.request.user.UpdateProfileRequest;
import com.brainblocks.backend.dto.response.location.AddressResponse;
import com.brainblocks.backend.dto.response.user.UserProfileResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.location.LocationDirectory;
import com.brainblocks.backend.service.location.LocationDirectory.ResolvedAddress;
import com.brainblocks.backend.util.AddressUtils;
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
    private final LocationDirectory locations;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        return toProfileResponse(getCurrentUserEntity());
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = getCurrentUserEntity();
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));

        // địa chỉ mặc định chỉ có ở Customer; tài khoản Admin bỏ qua các field này
        if (user instanceof Customer customer) {
            updateDefaultAddress(customer, request);
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
        // vô hiệu hóa mọi JWT đã phát trước đó (xem JwtService.isCurrentVersion); client phải đăng nhập lại
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    // để trống cả 4 field = xóa địa chỉ mặc định; có field nào thì phải đủ và đúng quan hệ tỉnh - huyện - xã
    private void updateDefaultAddress(Customer customer, UpdateProfileRequest request) {
        String detail = blankToNull(request.defaultAddressDetail());
        boolean empty = request.defaultProvinceCode() == null && request.defaultDistrictCode() == null
                && request.defaultWardCode() == null && detail == null;
        if (empty) {
            customer.setDefaultProvinceCode(null);
            customer.setDefaultDistrictCode(null);
            customer.setDefaultWardCode(null);
            customer.setDefaultAddressDetail(null);
            customer.setDefaultAddress(null);
            return;
        }
        ResolvedAddress address = locations.resolve(request.defaultProvinceCode(), request.defaultDistrictCode(),
                request.defaultWardCode());
        if (detail == null) {
            throw new IllegalArgumentException("Address detail is required");
        }
        customer.setDefaultProvinceCode(address.province().code());
        customer.setDefaultDistrictCode(address.district().code());
        customer.setDefaultWardCode(address.ward() == null ? null : address.ward().code());
        customer.setDefaultAddressDetail(detail);
        // đã có địa chỉ 3 cấp thì bỏ chuỗi địa chỉ kiểu cũ
        customer.setDefaultAddress(null);
    }

    // dùng chung cho cả AdminUserService để 2 phía trả cùng một định dạng
    public UserProfileResponse toProfileResponse(User user) {
        AddressResponse defaultShippingAddress = user instanceof Customer customer ? toAddress(customer) : null;
        String defaultAddress = defaultShippingAddress != null ? defaultShippingAddress.fullAddress()
                : user instanceof Customer customer ? customer.getDefaultAddress() : null;
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.isEnabled(),
                defaultAddress,
                defaultShippingAddress,
                user.getCreatedAt()
        );
    }

    // CustomUserDetails giữ entity đã detached từ lúc lọc JWT, nên phải load lại để JPA theo dõi thay đổi
    private User getCurrentUserEntity() {
        Long userId = currentUserProvider.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    // tên tỉnh / huyện / xã lấy theo mã từ dữ liệu địa chính; mã không còn hợp lệ thì coi như chưa có địa chỉ
    private AddressResponse toAddress(Customer customer) {
        if (customer.getDefaultProvinceCode() == null) {
            return null;
        }
        try {
            ResolvedAddress a = locations.resolve(customer.getDefaultProvinceCode(), customer.getDefaultDistrictCode(),
                    customer.getDefaultWardCode());
            String wardName = a.ward() == null ? null : a.ward().name();
            return new AddressResponse(a.province().code(), a.province().name(), a.district().code(),
                    a.district().name(), a.ward() == null ? null : a.ward().code(), wardName,
                    customer.getDefaultAddressDetail(), AddressUtils.format(customer.getDefaultAddressDetail(),
                    wardName, a.district().name(), a.province().name()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
