package com.brainblocks.backend.service.child;

import com.brainblocks.backend.dto.request.AssignProductRequest;
import com.brainblocks.backend.dto.request.ChildProfileRequest;
import com.brainblocks.backend.dto.response.ChildProductResponse;
import com.brainblocks.backend.dto.response.ChildProfileResponse;
import com.brainblocks.backend.dto.response.SkillResponse;
import com.brainblocks.backend.entity.ChildProduct;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.ChildProfileRepository;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChildProfileService {
    private final ChildProfileRepository childProfileRepository;
    private final ChildProductRepository childProductRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SkillRepository skillRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ChildProfileAccessGuard accessGuard;
    private final SkillProfileService skillProfileService;

    // ===== Hồ sơ trẻ =====

    @Transactional(readOnly = true)
    public List<ChildProfileResponse> getMyChildren() {
        return childProfileRepository.findAllByCustomerId(currentUserProvider.getCurrentUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChildProfileResponse getChild(Long childId) {
        return toResponse(accessGuard.getOwnedChildProfile(childId));
    }

    @Transactional
    public ChildProfileResponse createChild(ChildProfileRequest request) {
        // chỉ Customer mới có hồ sơ trẻ; tài khoản Admin không có bản ghi trong bảng customers
        Customer customer = customerRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new AccessDeniedException("Only customers can create child profiles"));

        ChildProfile child = ChildProfile.builder()
                .customer(customer)
                .build();
        applyRequest(child, request);
        ChildProfile saved = childProfileRepository.save(child);

        // khởi tạo luôn hồ sơ kỹ năng (mọi skill = 0) để mỗi hồ sơ trẻ luôn có đúng 1 SkillProfile
        skillProfileService.recalculateFor(saved);
        return toResponse(saved);
    }

    @Transactional
    public ChildProfileResponse updateChild(Long childId, ChildProfileRequest request) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        applyRequest(child, request);
        return toResponse(child);
    }

    @Transactional
    public void deleteChild(Long childId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);

        // ChatSession chỉ tham chiếu (không cascade) tới hồ sơ trẻ và cho phép null:
        // gỡ liên kết để giữ lịch sử chat, tránh lỗi khóa ngoại khi xóa
        child.getChatSessions().forEach(session -> session.setChildProfile(null));

        // tương tự với dòng đơn hàng "dành cho" bé: giữ lịch sử mua, chỉ bỏ tham chiếu
        childProfileRepository.detachOrderItems(child.getId());

        // SkillProfile, SkillScore, ChildProduct bị xóa theo nhờ cascade + orphanRemoval
        childProfileRepository.delete(child);
    }

    // ===== Sản phẩm của bé =====

    @Transactional(readOnly = true)
    public List<ChildProductResponse> getChildProducts(Long childId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        return childProductRepository.findAllWithProductByChildProfileId(child.getId()).stream()
                .map(this::toChildProductResponse)
                .toList();
    }

    @Transactional
    public ChildProductResponse assignProduct(Long childId, AssignProductRequest request) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);

        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive) // sản phẩm đã ẩn coi như không tồn tại với phía khách hàng
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // kiểm tra trước để trả lỗi rõ ràng, không để văng lỗi unique constraint từ DB
        if (childProductRepository.existsByChildProfileIdAndProductId(child.getId(), product.getId())) {
            throw new IllegalArgumentException("Product is already assigned to this child");
        }

        ChildProduct childProduct = ChildProduct.builder()
                .childProfile(child)
                .product(product)
                .source(ProductSource.MANUAL)
                .build();
        ChildProduct saved = childProductRepository.save(childProduct);

        skillProfileService.recalculateFor(child);
        return toChildProductResponse(saved);
    }

    @Transactional
    public void removeProduct(Long childId, Long childProductId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);

        // tìm theo cả childProfileId: chặn việc dùng hồ sơ của mình để gỡ sản phẩm của hồ sơ khác
        ChildProduct childProduct = childProductRepository.findByIdAndChildProfileId(childProductId, child.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Child product not found"));

        // gỡ khỏi collection để orphanRemoval xóa; nếu chỉ gọi repository.delete trong khi
        // collection đã được load thì cascade ALL sẽ lưu lại bản ghi lúc flush
        child.getChildProducts().remove(childProduct);

        skillProfileService.recalculateFor(child);
    }

    // ===== Mapping =====

    private void applyRequest(ChildProfile child, ChildProfileRequest request) {
        child.setName(request.name().trim());
        child.setBirthDate(request.birthDate());
        child.setGender(request.gender());
        child.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());

        // PUT thay toàn bộ: không gửi interestedSkillIds = bỏ hết nhóm kỹ năng quan tâm
        Set<Long> skillIds = request.interestedSkillIds() == null ? Set.of() : request.interestedSkillIds();
        List<Skill> skills = skillRepository.findAllById(skillIds);
        if (skills.size() != skillIds.size()) {
            throw new ResourceNotFoundException("Skill not found");
        }
        child.getInterestedSkills().clear();
        child.getInterestedSkills().addAll(skills);
    }

    private ChildProfileResponse toResponse(ChildProfile child) {
        List<SkillResponse> interestedSkills = child.getInterestedSkills().stream()
                .sorted(Comparator.comparing(Skill::getId))
                .map(skill -> new SkillResponse(skill.getId(), skill.getCode(), skill.getName()))
                .toList();

        return new ChildProfileResponse(
                child.getId(),
                child.getName(),
                child.getBirthDate(),
                child.getAge(),
                child.getGender(),
                child.getNote(),
                interestedSkills,
                child.getCreatedAt()
        );
    }

    private ChildProductResponse toChildProductResponse(ChildProduct childProduct) {
        Product product = childProduct.getProduct();
        return new ChildProductResponse(
                childProduct.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                childProduct.getSource().name(),
                childProduct.getAddedAt()
        );
    }
}
