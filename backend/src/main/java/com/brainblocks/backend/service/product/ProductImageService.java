package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.request.product.ReorderProductImagesRequest;
import com.brainblocks.backend.dto.response.product.ProductImageResponse;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductImage;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ProductImageRepository;
import com.brainblocks.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {
    // đuôi file cho phép; chặn .jsp/.php/... để không ai lợi dụng upload chạy mã tùy ý
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(Long productId) {
        return findProductWithImages(productId).getProductImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductImageResponse upload(Long productId, MultipartFile file) {
        Product product = findProductWithImages(productId);
        String extension = extractValidatedExtension(file);

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = resolveStoragePath(productId, storedFileName);
        writeToDisk(file, targetPath);

        // ảnh đầu tiên của sản phẩm tự động là thumbnail, để sản phẩm không bao giờ "trắng ảnh đại diện"
        boolean isFirstImage = product.getProductImages().isEmpty();
        int nextOrder = product.getProductImages().stream()
                .mapToInt(ProductImage::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        ProductImage image = ProductImage.builder()
                .product(product)
                .url(buildPublicUrl(productId, storedFileName))
                .displayOrder(nextOrder)
                .thumbnail(isFirstImage)
                .build();

        try {
            ProductImage saved = productImageRepository.save(image);
            product.getProductImages().add(saved);
            return toResponse(saved);
        } catch (RuntimeException ex) {
            // lưu DB thất bại thì dọn luôn file vừa ghi, tránh rác trên đĩa
            deleteFileQuietly(targetPath);
            throw ex;
        }
    }

    @Transactional
    public void delete(Long productId, Long imageId) {
        Product product = findProductWithImages(productId);
        ProductImage image = findImageInProduct(product, imageId);

        boolean wasThumbnail = image.isThumbnail();
        // gỡ khỏi collection để orphanRemoval xóa; xem lưu ý trong ChildProfileService.removeProduct
        product.getProductImages().remove(image);

        // ảnh vừa xóa là thumbnail mà vẫn còn ảnh khác -> tự chọn ảnh đầu tiên còn lại làm thumbnail mới
        if (wasThumbnail) {
            product.getProductImages().stream()
                    .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                    .ifPresent(next -> next.setThumbnail(true));
        }

        deleteFileQuietly(resolvePathFromUrl(image.getUrl()));
    }

    @Transactional
    public ProductImageResponse setThumbnail(Long productId, Long imageId) {
        Product product = findProductWithImages(productId);
        ProductImage target = findImageInProduct(product, imageId);

        // đã nạp cả collection nên tự xử lý tại chỗ, không cần thêm 1 query update riêng
        product.getProductImages().forEach(img -> img.setThumbnail(img.getId().equals(imageId)));
        return toResponse(target);
    }

    @Transactional
    public List<ProductImageResponse> reorder(Long productId, ReorderProductImagesRequest request) {
        Product product = findProductWithImages(productId);
        List<ProductImage> images = product.getProductImages();

        Set<Long> currentIds = images.stream().map(ProductImage::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> requestedIds = Set.copyOf(request.imageIds());
        if (!currentIds.equals(requestedIds)) {
            throw new IllegalArgumentException("imageIds must contain exactly the current images of this product");
        }

        Map<Long, ProductImage> imageById = images.stream()
                .collect(java.util.stream.Collectors.toMap(ProductImage::getId, img -> img));
        List<Long> orderedIds = request.imageIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            imageById.get(orderedIds.get(i)).setDisplayOrder(i);
        }

        return images.stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(this::toResponse)
                .toList();
    }

    // ===== Helpers =====

    private Product findProductWithImages(Long productId) {
        return productRepository.findWithImagesById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ProductImage findImageInProduct(Product product, Long imageId) {
        return product.getProductImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for this product"));
    }

    private String extractValidatedExtension(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalName = file.getOriginalFilename();
        int dotIndex = originalName == null ? -1 : originalName.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new IllegalArgumentException("File has no extension");
        }
        String extension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only jpg, jpeg, png, webp files are allowed");
        }
        // đối chiếu content-type thật của file với đuôi file, chặn đổi tên .php.jpg giả dạng ảnh
        String expectedContentType = CONTENT_TYPE_BY_EXTENSION.get(extension);
        if (file.getContentType() == null || !file.getContentType().equalsIgnoreCase(expectedContentType)) {
            throw new IllegalArgumentException("File content does not match its extension");
        }
        return extension;
    }

    private Path resolveStoragePath(Long productId, String storedFileName) {
        return Paths.get(uploadDir, "products", productId.toString(), storedFileName);
    }

    // URL public do WebConfig phục vụ; giữ tách biệt khỏi đường dẫn vật lý trên đĩa
    private String buildPublicUrl(Long productId, String storedFileName) {
        return "/uploads/products/" + productId + "/" + storedFileName;
    }

    private Path resolvePathFromUrl(String url) {
        // url dạng "/uploads/products/{id}/{file}" -> bỏ tiền tố "/uploads/" để map lại uploadDir
        String relative = url.startsWith("/uploads/") ? url.substring("/uploads/".length()) : url;
        return Paths.get(uploadDir, relative);
    }

    private void writeToDisk(MultipartFile file, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store uploaded file", ex);
        }
    }

    // xóa file vật lý là thao tác dọn dẹp phụ, không nên làm rớt cả transaction DB nếu thất bại
    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete file {}: {}", path, ex.getMessage());
        }
    }

    private ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getUrl(), image.getDisplayOrder(), image.isThumbnail());
    }
}
