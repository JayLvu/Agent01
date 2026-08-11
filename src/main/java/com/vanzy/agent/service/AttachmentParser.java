package com.vanzy.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 对话附件文本提取器
 *
 * 支持格式: pdf / docx / xlsx / pptx / txt / md / csv / json / xml / yaml / 代码文件
 * 依赖: Apache PDFBox 3.x + Apache POI 5.x (已在 pom.xml 中)
 *
 * @author JayLvu
 */
@Slf4j
@Component
public class AttachmentParser {

    /** 最大提取字符数(防止超大文件撑爆 LLM 上下文) */
    private static final int MAX_CHARS = 100_000;

    /**
     * 根据文件名后缀 + 原始字节解析出纯文本
     */
    public String parse(String fileName, byte[] bytes) throws IOException {
        String ext = detectExt(fileName);
        String text = switch (ext) {
            case "pdf" -> parsePdf(bytes);
            case "docx" -> parseDocx(bytes);
            case "xlsx" -> parseXlsx(bytes);
            case "pptx" -> parsePptx(bytes);
            case "doc" -> throw new UnsupportedOperationException("旧版 .doc 格式不支持,请转换为 .docx");
            case "xls" -> throw new UnsupportedOperationException("旧版 .xls 格式不支持,请转换为 .xlsx");
            case "ppt" -> throw new UnsupportedOperationException("旧版 .ppt 格式不支持,请转换为 .pptx");
            default -> parsePlainText(bytes); // txt/md/csv/json/xml/yaml/代码文件
        };
        if (text.length() > MAX_CHARS) {
            log.info("附件文本过长({}字符),截断到 {} 字符: {}", text.length(), MAX_CHARS, fileName);
            text = text.substring(0, MAX_CHARS) + "\n\n...[文件内容过长,已截断]";
        }
        return text;
    }

    public static String detectExt(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String parsePdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String parseDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) sb.append(t).append("\n");
            }
            // 表格中的文本
            doc.getTables().forEach(table -> {
                for (int r = 0; r < table.getNumberOfRows(); r++) {
                    table.getRow(r).getTableCells().forEach(cell ->
                            sb.append(cell.getText()).append("\t"));
                    sb.append("\n");
                }
            });
            return sb.toString();
        }
    }

    private String parseXlsx(byte[] bytes) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            DataFormatter fmt = new DataFormatter();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String val = fmt.formatCellValue(cell);
                        if (val != null && !val.isBlank()) sb.append(val).append("\t");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String parsePptx(byte[] bytes) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            int slideIdx = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                slideIdx++;
                sb.append("=== Slide ").append(slideIdx).append(" ===\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String parsePlainText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
