package com.brainblocks.backend.service.user;

import com.brainblocks.backend.dto.request.UpdateUserStatusRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.UserProfileResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.enums.Role;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getCustomers(String keyword, int page, int size) {
        // sort cố định phía server, không nhận field sort từ client (tránh sort theo field lạ hoặc password)
        // PageRequest.of tự ném IllegalArgumentException nếu page < 0 hoặc size < 1
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Customer> customers = (keyword == null || keyword.isBlank())
                ? customerRepository.findAll(pageable)
                : customerRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        keyword.trim(), keyword.trim(), pageable);

        return PageResponse.of(customers, userService::toProfileResponse);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUser(Long id) {
        return userService.toProfileResponse(findUser(id));
    }

    @Transactional
    public UserProfileResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        User user = findUser(id);

        // không cho khóa/mở tài khoản admin, tránh admin tự khóa mình hoặc khóa lẫn nhau
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot change status of an admin account");
        }
        user.setEnabled(request.enabled());
        return userService.toProfileResponse(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
