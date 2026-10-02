package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.enums.EvidenceKind;
import com.brainblocks.backend.util.FileSignatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu file bằng chứng khiếu nại vào thư mục riêng (app.complaint.evidence-dir), tách khỏi app.upload.dir
 * vốn được phục vụ công khai: video mở hàng là dữ liệu riêng tư của khách, chỉ tải qua API có kiểm tra quyền.
 */
@Slf4j
@Component
public class ComplaintEvidenceStorage {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");
    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "mp4", "video/mp4", "mov", "video/quicktime", "webm", "video/webm");

    private final Path root;
    private final long maxVideoBytes;

    public ComplaintEvidenceStorage(@Value("${app.complaint.evidence-dir:private-uploads/complaints}") String dir,
                                    @Value("${app.complaint.max-video-mb:100}") long maxVideoMb) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.maxVideoBytes = maxVideoMb * 1024 * 1024;
    }

    /**
     * Kiểm tra file trước khi lưu: đuôi, Content-Type, chữ ký nội dung và dung lượng.
     * Video mở hàng chỉ nhận video; ảnh / video tình trạng nhận cả hai.
     *
     * @return content type chuẩn của file
     */
    public String validate(MultipartFile file, EvidenceKind kind) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Evidence file is empty");
        }
        String extension = extension(file.getOriginalFilename());
        String videoType = VIDEO_TYPES.get(extension);
        String imageType = kind == EvidenceKind.CONDITION ? IMAGE_TYPES.get(extension) : null;
        String contentType = videoType != null ? videoType : imageType;
        if (contentType == null) {
            throw new IllegalArgumentException(kind == EvidenceKind.UNBOXING_VIDEO
                    ? "The unboxing video must be an mp4, mov or webm file"
                    : "Evidence must be a jpg, png, webp image or an mp4, mov, webm video");
        }
        // trình duyệt có thể khai video/mp4 cho file .mov hoặc để trống: chỉ tin chữ ký nội dung
        if (!FileSignatures.matches(file, contentType)) {
            throw new IllegalArgumentException("File content does not match its extension: " + file.getOriginalFilename());
        }
        long limit = videoType != null ? maxVideoBytes : MAX_IMAGE_BYTES;
        if (file.getSize() > limit) {
            throw new IllegalArgumentException("File is too large: " + file.getOriginalFilename()
                    + " (max " + limit / (1024 * 1024) + "MB)");
        }
        return contentType;
    }

    /**
     * Ghi file vào thư mục của khiếu nại. Nếu transaction hiện tại rollback (vd lưu DB lỗi sau đó)
     * thì file vừa ghi được xóa để không để rác trên đĩa.
     *
     * @return đường dẫn tương đối lưu trong ComplaintAttachment.storedPath
     */
    public String store(Long complaintId, MultipartFile file) {
        String storedPath = complaintId + "/" + UUID.randomUUID() + "." + extension(file.getOriginalFilename());
        Path target = resolve(storedPath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store evidence file", ex);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteQuietly(target);
                    }
                }
            });
        }
        return storedPath;
    }

    // xóa file đã lưu (dọn bằng chứng quá hạn); file không còn thì coi như đã xóa
    public void delete(String storedPath) {
        deleteQuietly(resolve(storedPath));
    }

    // đường dẫn tuyệt đối của file đã lưu; chặn đường dẫn trỏ ra ngoài thư mục bằng chứng
    public Path resolve(String storedPath) {
        Path path = root.resolve(storedPath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid evidence path");
        }
        return path;
    }

    private String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not delete evidence file {}", path, ex);
        }
    }
}
