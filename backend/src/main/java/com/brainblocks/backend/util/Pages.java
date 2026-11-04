package com.brainblocks.backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pages {
    private Pages() {
    }

    public static Pageable of(int page, int size, int maxSize, Sort sort) {
        PageRequest request = PageRequest.of(page, Math.min(size, maxSize), sort);
        // Giữ kiểm tra đầu vào của PageRequest, kẹp trang để offset không tràn int.
        return request.withPage(Math.min(page, Integer.MAX_VALUE / request.getPageSize()));
    }
}
