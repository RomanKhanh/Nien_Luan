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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ảnh sản phẩm do admin tải lên (đề 2.8). File nằm trong app.upload.dir, WebConfig phục vụ công khai qua /uploads/**.
 * Ảnh là thao tác riêng, không đi kèm PUT sản phẩm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {
    static final int MAX_IMAGES_PER_PRODUCT = 10;
    private static final String PUBLIC_PREFIX = "/uploads/";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(Long productId) {
        return toResponses(findProductWithImages(productId).getProductImages());
    }

    @Transactional
    public ProductImageResponse upload(Long productId, MultipartFile file) {
        Product product = findProductWithImages(productId);
        if (product.getProductImages().size() >= MAX_IMAGES_PER_PRODUCT) {
            throw new IllegalArgumentException("A product can have at most " + MAX_IMAGES_PER_PRODUCT + " images");
        }
        String extension = extractValidatedExtension(file);

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadRoot().resolve(Paths.get("products", productId.toString(), storedFileName));
        writeToDisk(file, targetPath);

        // ảnh đầu tiên của sản phẩm tự động là ảnh đại diện
        boolean isFirstImage = product.getProductImages().isEmpty();
        int nextOrder = product.getProductImages().stream()
                .mapToInt(ProductImage::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        ProductImage image = ProductImage.builder()
                .product(product)
                .url(PUBLIC_PREFIX + "products/" + productId + "/" + storedFileName)
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

        // ảnh vừa xóa là ảnh đại diện mà vẫn còn ảnh khác -> ảnh đứng đầu còn lại lên thay
        if (wasThumbnail) {
            product.getProductImages().stream()
                    .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                    .ifPresent(next -> next.setThumbnail(true));
        }
        deleteFileQuietly(resolveStoredPath(image.getUrl()));
    }

    @Transactional
    public ProductImageResponse setThumbnail(Long productId, Long imageId) {
        Product product = findProductWithImages(productId);
        ProductImage target = findImageInProduct(product, imageId);
        product.getProductImages().forEach(img -> img.setThumbnail(img.getId().equals(imageId)));
        return toResponse(target);
    }

    @Transactional
    public List<ProductImageResponse> reorder(Long productId, ReorderProductImagesRequest request) {
        Product product = findProductWithImages(productId);
        Map<Long, ProductImage> imageById = product.getProductImages().stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        List<Long> orderedIds = request.imageIds();
        if (orderedIds.size() != imageById.size() || !new HashSet<>(orderedIds).equals(imageById.keySet())) {
            throw new IllegalArgumentException("imageIds must contain exactly the current images of this product");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            imageById.get(orderedIds.get(i)).setDisplayOrder(i);
        }
        return toResponses(product.getProductImages());
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
        // Content-Type do client tự khai nên chưa đủ: đọc thêm vài byte đầu để chặn file đổi đuôi giả dạng ảnh
        String expectedContentType = CONTENT_TYPE_BY_EXTENSION.get(extension);
        if (!expectedContentType.equalsIgnoreCase(file.getContentType()) || !hasImageSignature(file, expectedContentType)) {
            throw new IllegalArgumentException("File content does not match its extension");
        }
        return extension;
    }

    private boolean hasImageSignature(MultipartFile file, String contentType) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded file", ex);
        }
        return switch (contentType) {
            case "image/jpeg" -> startsWith(head, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
            case "image/png" -> startsWith(head, 0, (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            // RIFF <4 byte kích thước> WEBP
            case "image/webp" -> startsWith(head, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && startsWith(head, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private boolean startsWith(byte[] data, int offset, byte... signature) {
        if (data.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    // null khi ảnh không phải file do hệ thống lưu (vd URL ngoài của dữ liệu mẫu) hoặc đường dẫn trỏ ra ngoài thư mục upload
    private Path resolveStoredPath(String url) {
        if (url == null || !url.startsWith(PUBLIC_PREFIX)) {
            return null;
        }
        Path root = uploadRoot();
        Path path = root.resolve(url.substring(PUBLIC_PREFIX.length())).normalize();
        return path.startsWith(root) ? path : null;
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
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete file {}: {}", path, ex.getMessage());
        }
    }

    private List<ProductImageResponse> toResponses(List<ProductImage> images) {
        return images.stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(this::toResponse)
                .toList();
    }

    private ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getUrl(), image.getDisplayOrder(), image.isThumbnail());
    }
}
