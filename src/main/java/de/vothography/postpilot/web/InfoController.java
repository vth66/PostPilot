package de.vothography.postpilot.web;

import de.vothography.postpilot.service.AppPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Auskunft über die laufende Instanz. Nützlich nach einem Update: Zeigt die Startzeit noch
 * gestern an, läuft der Container nicht mit der neuen JAR — dann wurde er nicht neu gestartet.
 */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    private static final DateTimeFormatter UHRZEIT =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private final Instant gestartet = Instant.now();
    private final AppPaths paths;

    public InfoController(AppPaths paths) {
        this.paths = paths;
    }

    public record Info(String gestartet, String laufzeit, String datenverzeichnis) {
    }

    @GetMapping
    public Info info() {
        Duration seitdem = Duration.between(gestartet, Instant.now());
        String laufzeit = seitdem.toHours() > 0
                ? seitdem.toHours() + " h " + (seitdem.toMinutesPart()) + " min"
                : seitdem.toMinutes() + " min";
        return new Info(UHRZEIT.format(gestartet), laufzeit, paths.dataDir().toAbsolutePath().toString());
    }
}
