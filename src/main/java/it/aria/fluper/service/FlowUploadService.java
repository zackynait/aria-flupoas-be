package it.aria.fluper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FlowUploadService {
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> REQUIRED_KEYS = Set.of("PERIODO", "CODICE_ENTE", "CODICE_FISCALE", "PROGRESSIVO");
    private static final Set<String> PERSONAL_FIELDS = Set.of("COGNOME", "NOME", "SESSO", "DATA_NASCITA");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FlowUploadService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UploadResult receive(String flow, String period, String submissionType, MultipartFile file) {
        ParsedUpload parsed = parse(flow, period, file);
        String uploadId = UUID.randomUUID().toString();
        String status = parsed.issues().isEmpty() ? "RICEVUTO" : "SCARTATO";
        jdbcTemplate.update("INSERT INTO FLUPER_INVIO (ID_INVIO, FLUSSO, PERIODO, TIPO_INVIO, STATO, DATA_RICEZIONE) VALUES (?, ?, ?, ?, ?, ?)",
                uploadId, flow, period, submissionType, status, new Timestamp(System.currentTimeMillis()));
        if (file != null && file.getSize() <= MAX_FILE_BYTES) {
            jdbcTemplate.update("INSERT INTO FLUPER_FILE_INVIO (ID_FILE, ID_INVIO, NOME_FILE, CONTENT_TYPE, DIMENSIONE) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), uploadId, safeFilename(file.getOriginalFilename()), file.getContentType(), file.getSize());
        }
        for (ValidationIssue issue : parsed.issues()) {
            jdbcTemplate.update("INSERT INTO FLUPER_ANOMALIA (ID_ANOMALIA, ID_INVIO, CODICE, DESCRIZIONE, NUMERO_RIGA) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), uploadId, issue.codice(), issue.descrizione(), issue.riga());
        }
        if (parsed.issues().isEmpty()) {
            for (ParsedRow row : parsed.rows()) {
                String rowId = UUID.randomUUID().toString();
                String json;
                try {
                    json = objectMapper.writeValueAsString(row.data());
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Impossibile serializzare la riga del flusso", exception);
                }
                jdbcTemplate.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO FLUPER_RIGA_INVIO (ID_RIGA, ID_INVIO, NUMERO_RIGA, FLUSSO, PERIODO, CODICE_ENTE, CODICE_FISCALE, PROGRESSIVO, DATI_JSON) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    statement.setString(1, rowId);
                    statement.setString(2, uploadId);
                    statement.setInt(3, row.number());
                    statement.setString(4, flow);
                    statement.setString(5, row.period());
                    statement.setString(6, row.entityCode());
                    statement.setString(7, row.taxCode());
                    statement.setString(8, row.progressive());
                    statement.setCharacterStream(9, new StringReader(json), json.length());
                    return statement;
                });
            }
        }
        return new UploadResult(uploadId, status, flow, period, parsed.rows().size(), parsed.issues().size(), parsed.issues());
    }

    private ParsedUpload parse(String flow, String selectedPeriod, MultipartFile file) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ParsedRow> rows = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            issues.add(new ValidationIssue("FILE_VUOTO", "Selezionare un file non vuoto", null));
            return new ParsedUpload(rows, issues);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            issues.add(new ValidationIssue("FILE_TROPPO_GRANDE", "La dimensione massima consentita è 20 MB", null));
            return new ParsedUpload(rows, issues);
        }
        String filename = safeFilename(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            issues.add(new ValidationIssue("FORMATO_NON_SUPPORTATO", "Sono accettati file Excel .xls o .xlsx", null));
            return new ParsedUpload(rows, issues);
        }
        try (InputStream uploadedStream = file.getInputStream();
             InputStream templateStream = new ClassPathResource(templateName(flow)).getInputStream();
             Workbook workbook = WorkbookFactory.create(uploadedStream);
             Workbook template = WorkbookFactory.create(templateStream)) {
            var sheet = findSheet(workbook, "FLUSSO_" + flow);
            if (sheet == null) {
                issues.add(new ValidationIssue("FOGLIO_MANCANTE", "Nel file manca il foglio FLUSSO_" + flow, null));
                return new ParsedUpload(rows, issues);
            }
            Set<String> allowedHeaders = readHeaders(template.getSheetAt(0).getRow(0), new DataFormatter());
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                issues.add(new ValidationIssue("INTESTAZIONI_MANCANTI", "Il foglio non contiene le intestazioni del tracciato", 1));
                return new ParsedUpload(rows, issues);
            }
            Map<String, Integer> columns = new HashMap<>();
            for (int index = 0; index < headerRow.getLastCellNum(); index++) {
                String header = normalize(formatter.formatCellValue(headerRow.getCell(index)));
                if (header.isBlank()) continue;
                if (columns.putIfAbsent(header, index) != null) {
                    issues.add(new ValidationIssue("INTESTAZIONE_DUPLICATA", "Intestazione duplicata: " + header, 1));
                }
            }
            for (String required : allowedHeaders) {
                if (!columns.containsKey(required)) {
                    issues.add(new ValidationIssue("COLONNA_MANCANTE", "Manca la colonna obbligatoria " + required, 1));
                }
            }
            for (String required : REQUIRED_KEYS) {
                if (!columns.containsKey(required)) {
                    issues.add(new ValidationIssue("COLONNA_CHIAVE_MANCANTE", "Manca la colonna chiave " + required, 1));
                }
            }
            if (!issues.isEmpty()) return new ParsedUpload(rows, issues);

            Set<String> seenKeys = new HashSet<>();
            int populatedRows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                boolean populated = false;
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    if (!formatter.formatCellValue(row.getCell(column)).isBlank()) { populated = true; break; }
                }
                if (!populated) continue;
                populatedRows++;
                int excelRow = rowIndex + 1;
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> column : columns.entrySet()) {
                    String field = column.getKey();
                    if (allowedHeaders.contains(field) && !PERSONAL_FIELDS.contains(field)) {
                        values.put(field, formatter.formatCellValue(row.getCell(column.getValue())).trim());
                    }
                }
                String rowPeriod = values.getOrDefault("PERIODO", "").replace("-", "");
                String normalizedSelectedPeriod = selectedPeriod.replace("-", "");
                String entity = values.getOrDefault("CODICE_ENTE", "");
                String taxCode = values.getOrDefault("CODICE_FISCALE", "");
                String progressive = values.getOrDefault("PROGRESSIVO", "");
                if (rowPeriod.isBlank() || entity.isBlank() || taxCode.isBlank() || progressive.isBlank()) {
                    issues.add(new ValidationIssue("CHIAVE_RIGA_INCOMPLETA", "Periodo, ente, codice fiscale e progressivo sono obbligatori", excelRow));
                    continue;
                }
                if (!rowPeriod.equals(normalizedSelectedPeriod)) {
                    issues.add(new ValidationIssue("PERIODO_NON_COHERENTE", "Il periodo della riga non corrisponde al periodo selezionato", excelRow));
                }
                if (!taxCode.toUpperCase(Locale.ROOT).matches("[A-Z0-9]{16}")) {
                    issues.add(new ValidationIssue("CODICE_FISCALE_NON_VALIDO", "Il codice fiscale deve contenere 16 caratteri alfanumerici", excelRow));
                }
                if (!progressive.matches("[1-9]")) {
                    issues.add(new ValidationIssue("PROGRESSIVO_NON_VALIDO", "Il progressivo deve essere un numero da 1 a 9", excelRow));
                }
                String key = entity + "|" + rowPeriod + "|" + taxCode + "|" + progressive;
                if (!seenKeys.add(key)) issues.add(new ValidationIssue("RIGA_DUPLICATA", "Record duplicato per ente, periodo, codice fiscale e progressivo", excelRow));
                values.keySet().removeAll(REQUIRED_KEYS);
                rows.add(new ParsedRow(excelRow, rowPeriod, entity, taxCode, progressive, values));
            }
            if (populatedRows == 0) issues.add(new ValidationIssue("DATI_ASSENTI", "Inserire almeno una riga dati sotto le intestazioni", null));
        } catch (Exception exception) {
            issues.add(new ValidationIssue("FILE_NON_LEGGIBILE", "Il file Excel è danneggiato o non leggibile", null));
        }
        return new ParsedUpload(rows, issues);
    }

    private Set<String> readHeaders(Row headerRow, DataFormatter formatter) {
        Set<String> headers = new HashSet<>();
        if (headerRow == null) return headers;
        for (int index = 0; index < headerRow.getLastCellNum(); index++) {
            String header = normalize(formatter.formatCellValue(headerRow.getCell(index)));
            if (!header.isBlank() && !PERSONAL_FIELDS.contains(header)) headers.add(header);
        }
        return headers;
    }

    private org.apache.poi.ss.usermodel.Sheet findSheet(Workbook workbook, String name) {
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            var sheet = workbook.getSheetAt(index);
            if (sheet.getSheetName().equalsIgnoreCase(name)) return sheet;
        }
        return null;
    }

    private String templateName(String flow) {
        return flow.equals("A") ? "templates/template_flusso_a.xls" : "templates/template_flusso_b.xls";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "upload.xls";
        return filename.replaceAll("[\\\\/\\r\\n\\t]", "_");
    }

    private record ParsedRow(int number, String period, String entityCode, String taxCode, String progressive,
                             Map<String, String> data) {}
    private record ParsedUpload(List<ParsedRow> rows, List<ValidationIssue> issues) {}
    public record ValidationIssue(String codice, String descrizione, Integer riga) {}
    public record UploadResult(String idInvio, String esito, String flusso, String periodo, int numeroRighe,
                               int numeroAnomalie, List<ValidationIssue> anomalie) {}
}
