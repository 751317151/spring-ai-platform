package com.huah.ai.platform.rag.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Excel 文档解析器
 * 将 Excel 每个 Sheet 解析为若干 Document
 */
@Slf4j
@Component
public class ExcelDocumentParser {

    public List<Document> parse(Resource resource) {
        List<Document> documents = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(resource.getInputStream())) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetContent = parseSheet(sheet);
                if (!sheetContent.isBlank()) {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("sheet_name", sheet.getSheetName());
                    meta.put("sheet_index", i);
                    documents.add(new Document(sheetContent, meta));
                }
            }
        } catch (IOException e) {
            log.error("Excel 解析失败: {}", e.getMessage());
            throw new RuntimeException("Excel 解析失败", e);
        }
        return documents;
    }

    private String parseSheet(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sheet: ").append(sheet.getSheetName()).append("\n\n");

        // 提取表头
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                headers.add(getCellValue(cell));
            }
        }

        // 提取数据行（最多 1000 行，防止过大）
        int maxRows = Math.min(sheet.getLastRowNum(), 1000);
        for (int i = 1; i <= maxRows; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            StringBuilder rowSb = new StringBuilder();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                String value = cell != null ? getCellValue(cell) : "";
                if (!value.isBlank()) {
                    rowSb.append(headers.size() > j ? headers.get(j) : "列" + j)
                            .append(": ").append(value).append(" | ");
                }
            }
            if (!rowSb.isEmpty()) {
                sb.append(rowSb).append("\n");
            }
        }
        return sb.toString();
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
