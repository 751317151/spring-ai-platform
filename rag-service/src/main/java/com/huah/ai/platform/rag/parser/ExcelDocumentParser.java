package com.huah.ai.platform.rag.parser;

import com.huah.ai.platform.common.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses Excel files into Spring AI documents.
 * Each sheet is converted into one document payload.
 */
@Slf4j
@Component
public class ExcelDocumentParser {

    private static final int MAX_ROWS_PER_SHEET = 1000;
    private static final String METADATA_SHEET_NAME = "sheet_name";
    private static final String METADATA_SHEET_INDEX = "sheet_index";
    private static final String MESSAGE_PARSE_FAILED = "Failed to parse Excel document";
    private static final String FALLBACK_COLUMN_PREFIX = "Column";

    public List<Document> parse(Resource resource) {
        List<Document> documents = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(resource.getInputStream())) {
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                String sheetContent = parseSheet(sheet);
                if (!sheetContent.isBlank()) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put(METADATA_SHEET_NAME, sheet.getSheetName());
                    metadata.put(METADATA_SHEET_INDEX, index);
                    documents.add(new Document(sheetContent, metadata));
                }
            }
        } catch (IOException exception) {
            log.error("{}: {}", MESSAGE_PARSE_FAILED, exception.getMessage(), exception);
            throw new AiServiceException(MESSAGE_PARSE_FAILED, exception);
        }
        return documents;
    }

    private String parseSheet(Sheet sheet) {
        StringBuilder builder = new StringBuilder();
        builder.append("Sheet: ").append(sheet.getSheetName()).append("\n\n");

        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                headers.add(getCellValue(cell));
            }
        }

        int maxRows = Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET);
        for (int rowIndex = 1; rowIndex <= maxRows; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            StringBuilder rowBuilder = new StringBuilder();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                Cell cell = row.getCell(columnIndex);
                String value = cell != null ? getCellValue(cell) : "";
                if (!value.isBlank()) {
                    rowBuilder.append(resolveHeader(headers, columnIndex))
                            .append(": ")
                            .append(value)
                            .append(" | ");
                }
            }
            if (!rowBuilder.isEmpty()) {
                builder.append(rowBuilder).append('\n');
            }
        }
        return builder.toString();
    }

    private String resolveHeader(List<String> headers, int columnIndex) {
        if (headers.size() > columnIndex && !headers.get(columnIndex).isBlank()) {
            return headers.get(columnIndex);
        }
        return FALLBACK_COLUMN_PREFIX + columnIndex;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
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
