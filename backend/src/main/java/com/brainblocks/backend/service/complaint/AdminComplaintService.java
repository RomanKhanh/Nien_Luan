package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.dto.request.complaint.UpdateComplaintStatusRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.entity.ComplaintItem;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.AdminRepository;
import com.brainblocks.backend.repository.ComplaintItemRepository;
import com.brainblocks.backend.repository.ComplaintRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminComplaintService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ComplaintRepository complaintRepository;
    private final ComplaintItemRepository complaintItemRepository;
    private final AdminRepository adminRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ComplaintService complaintService;

    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> getComplaints(ComplaintStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Complaint> complaints = status == null
                ? complaintRepository.findAll(pageable)
                : complaintRepository.findByStatus(status, pageable);

        // batch-fetch items cho cả trang trong 1 câu, thay vì để mỗi complaint tự lazy-load (N+1)
        List<Long> complaintIds = complaints.getContent().stream().map(Complaint::getId).toList();
        Map<Long, List<ComplaintItem>> itemsByComplaintId = complaintIds.isEmpty()
                ? Map.of()
                : complaintItemRepository.findAllWithOrderItemAndProductByComplaintIdIn(complaintIds).stream()
                .collect(Collectors.groupingBy(ci -> ci.getComplaint().getId()));

        return PageResponse.of(complaints,
                c -> complaintService.toResponse(c, itemsByComplaintId.getOrDefault(c.getId(), List.of())));
    }

    @Transactional
    public ComplaintResponse updateStatus(Long complaintId, UpdateComplaintStatusRequest request) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));

        complaint.setStatus(request.status());
        if (request.response() != null && !request.response().isBlank()) {
            complaint.setResponse(request.response().trim());
        }
        complaint.setHandledBy(adminRepository.getReferenceById(currentUserProvider.getCurrentUserId()));
        complaint.setHandledAt(LocalDateTime.now());

        return complaintService.toResponse(complaint);
    }
}