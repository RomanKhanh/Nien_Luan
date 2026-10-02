package com.brainblocks.backend.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Content-Type do client tự khai nên chưa đủ tin: đọc vài byte đầu file để chặn file đổi đuôi
 * giả dạng ảnh / video. Dùng cho ảnh sản phẩm và bằng chứng khiếu nại.
 */
public final class FileSignatures {
    private static final int HEAD_LENGTH = 12;

    private FileSignatures() {
    }

    // true nếu nội dung file khớp contentType (chỉ hỗ trợ các kiểu liệt kê dưới đây)
    public static boolean matches(MultipartFile file, String contentType) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(HEAD_LENGTH);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded file", ex);
        }
        return switch (contentType) {
            case "image/jpeg" -> startsWith(head, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
            case "image/png" -> startsWith(head, 0, (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            // RIFF <4 byte kích thước> WEBP
            case "image/webp" -> startsWith(head, 0, ascii("RIFF")) && startsWith(head, 8, ascii("WEBP"));
            // MP4 / MOV (ISO base media): <4 byte kích thước> ftyp ...; MOV cũ có thể mở đầu bằng moov/mdat/wide
            case "video/mp4" -> startsWith(head, 4, ascii("ftyp"));
            case "video/quicktime" -> startsWith(head, 4, ascii("ftyp")) || startsWith(head, 4, ascii("moov"))
                    || startsWith(head, 4, ascii("mdat")) || startsWith(head, 4, ascii("wide"));
            // WebM / Matroska: EBML header
            case "video/webm" -> startsWith(head, 0, (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3);
            default -> false;
        };
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean startsWith(byte[] data, int offset, byte... signature) {
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
}
