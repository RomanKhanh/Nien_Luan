package com.brainblocks.backend.service.ai;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class KnowledgeDocumentStorage {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 1_000_000;
    private final Path root;

    public KnowledgeDocumentStorage(@Value("${app.knowledge.dir:knowledge-files}") String directory,
            @Value("${app.upload.dir:uploads}") String publicDirectory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        if (root.startsWith(Path.of(publicDirectory).toAbsolutePath().normalize())) {
            throw new IllegalStateException("Knowledge files must be outside public uploads");
        }
    }

    public String fileType(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Document file is required");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Document must not exceed 10 MB");
        String name = file.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String type = dot < 0 ? "" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
        if (!Set.of("PDF", "DOCX", "TXT", "MD").contains(type)) {
            throw new IllegalArgumentException("Only PDF, DOCX, TXT and MD documents are supported");
        }
        return type;
    }

    public String store(MultipartFile file, String type) {
        String name = UUID.randomUUID() + "." + type.toLowerCase(Locale.ROOT);
        Path target = resolve(name);
        try {
            // Đọc có giới hạn ngay cả khi MultipartFile báo size không đúng.
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes((int) MAX_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid document size");
            if (type.equals("PDF") && !new String(bytes, 0, Math.min(5, bytes.length), StandardCharsets.US_ASCII)
                    .equals("%PDF-")) throw new IllegalArgumentException("Invalid PDF file");
            if (type.equals("DOCX") && (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K')) {
                throw new IllegalArgumentException("Invalid DOCX file");
            }
            if (type.equals("TXT") || type.equals("MD")) decodeText(bytes);
            Files.createDirectories(root);
            Files.write(target, bytes);
            return name;
        } catch (IOException ex) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new IllegalStateException("Could not store knowledge document", ex);
        }
    }

    public String readText(String storedName, String type) {
        Path path = resolve(storedName);
        try {
            String text;
            switch (type) {
                case "PDF" -> {
                    try (var pdf = Loader.loadPDF(path.toFile())) {
                        text = new PDFTextStripper().getText(pdf);
                    }
                }
                case "DOCX" -> {
                    try (var input = Files.newInputStream(path);
                         var document = new XWPFDocument(input);
                         var extractor = new XWPFWordExtractor(document)) {
                        text = extractor.getText();
                    }
                }
                case "TXT", "MD" -> text = decodeText(Files.readAllBytes(path));
                default -> throw new IllegalArgumentException("Unsupported document type");
            }
            text = text.replace("\uFEFF", "").replace("\r\n", "\n").strip();
            if (text.isBlank()) throw new IllegalArgumentException("Document has no extractable text (scanned PDF needs OCR)");
            if (text.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("Document contains too much text");
            return text;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read document content", ex);
        }
    }

    private String decodeText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("Text document contains binary data");
            return text;
        } catch (java.nio.charset.CharacterCodingException ex) {
            throw new IllegalArgumentException("TXT and MD documents must use UTF-8", ex);
        }
    }

    public List<String> splitText(String text) {
        // Đoạn tối đa 1200 ký tự, chồng lấn 200 để giữ ngữ cảnh ở chỗ cắt.
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 1200, text.length());
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf(' ', end));
                if (boundary > start + 600) end = boundary;
            }
            String part = text.substring(start, end).strip();
            if (!part.isBlank()) parts.add(part);
            if (parts.size() > 1000) throw new IllegalArgumentException("Document has too many chunks");
            if (end == text.length()) break;
            start = end - 200;
        }
        return parts;
    }

    public void delete(String storedName) {
        try { Files.deleteIfExists(resolve(storedName)); }
        catch (IOException ex) { throw new IllegalStateException("Could not delete knowledge document", ex); }
    }

    private Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root) || !path.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid knowledge document path");
        }
        return path;
    }
}
