package it.aria.fluper.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private final DataSource dataSource;

    public ApiController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "fluper-api", "apiVersion", "v1");
    }

    @GetMapping("/db/connection")
    public ResponseEntity<Map<String, Object>> databaseConnection() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1 FROM DUAL")) {
            boolean connected = result.next() && result.getInt(1) == 1;
            return ResponseEntity.ok(Map.of("status", connected ? "UP" : "DOWN", "database", "oracle",
                    "connected", connected, "message", connected ? "Connessione al database riuscita" : "Query di verifica non riuscita"));
        } catch (SQLException exception) {
            log.warn("Oracle connection check failed (SQLState={}, vendorCode={}): {}",
                    exception.getSQLState(), exception.getErrorCode(), exception.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN", "database", "oracle", "connected", false,
                    "message", "Connessione al database non riuscita"));
        }
    }

    @GetMapping("/catalogo")
    public ApiCatalog catalog() {
        return new ApiCatalog("FLUPER 2.0", "v1", List.of(
                new ApiInfo("GET", "/api/v1/rilevazioni", "Elenco rilevazioni", "Rilevazioni"),
                new ApiInfo("GET", "/api/v1/enti", "Elenco enti", "Rilevazioni, conferimenti"),
                new ApiInfo("GET", "/api/v1/enti/tipologie", "Tipologie ente", "Rilevazioni"),
                new ApiInfo("GET", "/api/v1/conferimenti", "Elenco conferimenti", "Conferimenti"),
                new ApiInfo("GET", "/api/v1/notifiche", "Notifiche utente", "Notifiche"),
                new ApiInfo("GET", "/api/v1/documenti", "Documenti disponibili", "Documenti"),
                new ApiInfo("GET", "/api/v1/assistenza", "Canali di assistenza", "Assistenza"),
                new ApiInfo("GET", "/api/v1/governo/metriche", "Metriche cruscotto", "Governo"),
                new ApiInfo("GET", "/api/v1/reportistica", "Report disponibili", "Reportistica")
        ));
    }

    @GetMapping("/rilevazioni")
    public List<SurveySummary> surveys() {
        return List.of(
                new SurveySummary("RIL-2026-001", "Personale 2026", "Personale", "Personale dipendente", "2026", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"), 42, 31, "IN_CORSO"),
                new SurveySummary("RIL-2026-002", "PTFP 2026–2028", "Programmazione", "Piano triennale dei fabbisogni di personale", "2026-2028", LocalDate.parse("2026-09-15"), LocalDate.parse("2026-11-15"), 42, 24, "IN_CORSO"),
                new SurveySummary("RIL-2026-003", "Assenza dati 2026", "Personale", "Assenze", "2026", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-11-30"), 42, 38, "IN_CORSO"),
                new SurveySummary("RIL-2026-004", "Organigramma 2026", "Organizzazione", "Organigramma aziendale", "2026", null, null, 0, 0, "BOZZA")
        );
    }

    @GetMapping("/enti/tipologie")
    public List<String> entityTypes() { return List.of("ATS", "ASST", "IRCCS", "AREU"); }

    @GetMapping("/enti")
    public List<EntitySummary> entities() {
        return List.of(new EntitySummary("E001", "ASST Grande Ospedale", "ASST"),
                new EntitySummary("E002", "ATS Bergamo", "ATS"),
                new EntitySummary("E003", "ASST Lariana", "ASST"),
                new EntitySummary("E004", "ATS Val Padana", "ATS"),
                new EntitySummary("E005", "ASST Spedali Civili", "ASST"),
                new EntitySummary("E006", "IRCCS Istituto Oncologico Lombardo", "IRCCS"),
                new EntitySummary("E007", "AREU Lombardia", "AREU"));
    }

    @GetMapping("/conferimenti")
    public List<SubmissionSummary> submissions() {
        return List.of(new SubmissionSummary("CONF-2026-001", "E001", "ASST Grande Ospedale", "RIL-2026-001", "Personale 2026", "2026-10-22T10:42:00+02:00", "SUPERATI", "DA_VALIDARE", 3),
                new SubmissionSummary("CONF-2026-002", "E002", "ATS Bergamo", "RIL-2026-002", "PTFP 2026–2028", "2026-10-22T09:30:00+02:00", "ANOMALIE", "NON_DISPONIBILE", 2),
                new SubmissionSummary("CONF-2026-003", "E004", "ATS Val Padana", "RIL-2026-001", "Personale 2026", "2026-10-22T08:55:00+02:00", "SUPERATI", "VALIDATO", 4),
                new SubmissionSummary("CONF-2026-004", "E003", "ASST Lariana", "RIL-2026-001", "Personale 2026", "2026-10-21T16:00:00+02:00", "WARNING", "GIUSTIFICAZIONE", 1));
    }

    @GetMapping("/notifiche")
    public List<NotificationSummary> notifications() {
        return List.of(new NotificationSummary("NOT-001", "Scadenza rilevazione Personale 2026", "La scadenza è fissata al 31 ottobre 2026.", "2026-10-22T09:00:00+02:00", false),
                new NotificationSummary("NOT-002", "Conferimento ricevuto", "Il file inviato da ASST Grande Ospedale è stato ricevuto.", "2026-10-22T10:42:00+02:00", false));
    }

    @GetMapping("/documenti")
    public List<DocumentSummary> documents() {
        return List.of(new DocumentSummary("DOC-001", "Manuale operativo FLUPER 2.0", "Manuale", "2026-10-01"),
                new DocumentSummary("DOC-002", "Tracciato Personale", "Template", "2026-09-15"));
    }

    @GetMapping("/assistenza")
    public Map<String, Object> support() { return Map.of("email", "assistenza@fluper.example", "orari", "Lunedì–Venerdì, 09:00–17:00"); }

    @GetMapping("/governo/metriche")
    public Map<String, Integer> metrics() { return Map.of("rilevazioniAttive", 4, "entiCoinvolti", 42, "conferimentiRicevuti", 42, "anomalieAperte", 7); }

    @GetMapping("/reportistica")
    public List<Map<String, String>> reports() {
        return List.of(Map.of("id", "REP-001", "titolo", "Avanzamento rilevazioni", "stato", "DISPONIBILE"),
                Map.of("id", "REP-002", "titolo", "Esiti conferimenti", "stato", "DISPONIBILE"));
    }

    public record ApiCatalog(String prodotto, String versione, List<ApiInfo> api) {}
    public record ApiInfo(String metodo, String percorso, String descrizione, String schermata) {}
    public record SurveySummary(String id, String titolo, String area, String flusso, String periodo, LocalDate dataInizio, LocalDate scadenza, int entiCoinvolti, int entiCompletati, String stato) {}
    public record EntitySummary(String id, String nome, String tipologia) {}
    public record SubmissionSummary(String id, String enteId, String ente, String rilevazioneId, String rilevazione, String ricevutoIl, String controlli, String validazione, int versione) {}
    public record NotificationSummary(String id, String titolo, String messaggio, String data, boolean letta) {}
    public record DocumentSummary(String id, String titolo, String categoria, String aggiornatoIl) {}
}
