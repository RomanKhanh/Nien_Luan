package com.brainblocks.backend.service.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class KnowledgeDocumentStorageTest {
    @TempDir Path directory;

    @Test
    void readsRealPdfAndDocxFiles() throws Exception {
        KnowledgeDocumentStorage storage = new KnowledgeDocumentStorage(directory.toString(), directory.resolve("public").toString());
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("STEM education");
                stream.endText();
            }
            pdf.save(pdfBytes);
        }
        String pdfFile = storage.store(new MockMultipartFile("file", "guide.pdf", "application/pdf", pdfBytes.toByteArray()), "PDF");
        assertThat(storage.readText(pdfFile, "PDF")).contains("STEM education");

        ByteArrayOutputStream docxBytes = new ByteArrayOutputStream();
        try (XWPFDocument docx = new XWPFDocument()) {
            docx.createParagraph().createRun().setText("Đồ chơi phát triển sáng tạo");
            docx.write(docxBytes);
        }
        String docxFile = storage.store(new MockMultipartFile("file", "guide.docx", "application/octet-stream", docxBytes.toByteArray()), "DOCX");
        assertThat(storage.readText(docxFile, "DOCX")).contains("sáng tạo");
    }

    @Test
    void knowledgeDirectoryCannotBePublicAndPathsCannotEscapeIt() {
        assertThatThrownBy(() -> new KnowledgeDocumentStorage(directory.resolve("knowledge").toString(), directory.toString()))
                .isInstanceOf(IllegalStateException.class);
        KnowledgeDocumentStorage storage = new KnowledgeDocumentStorage(directory.toString(), directory.resolve("public").toString());
        assertThatThrownBy(() -> storage.readText("../outside.txt", "TXT")).isInstanceOf(IllegalArgumentException.class);
    }
}
