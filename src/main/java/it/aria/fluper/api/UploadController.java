package it.aria.fluper.api;

import it.aria.fluper.service.FlowUploadService;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/conferimenti")
public class UploadController {
    private final FlowUploadService uploadService;

    public UploadController(FlowUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @GetMapping("/templates/{flusso}")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable @Pattern(regexp = "A|B") String flusso) {
        String fileName = "template_flusso_" + flusso.toLowerCase() + ".xls";
        Resource template = new ClassPathResource("templates/" + fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(template);
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FlowUploadService.UploadResult> upload(
            @RequestParam @Pattern(regexp = "A|B") String flusso,
            @RequestParam @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String periodo,
            @RequestParam(defaultValue = "TOTALE") @Pattern(regexp = "TOTALE|PARZIALE") String tipoInvio,
            @RequestParam("file") MultipartFile file) {
        FlowUploadService.UploadResult result = uploadService.receive(flusso, periodo, tipoInvio, file);
        return ResponseEntity.status(result.esito().equals("RICEVUTO") ? 201 : 422).body(result);
    }
}
