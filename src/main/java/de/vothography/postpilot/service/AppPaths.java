package de.vothography.postpilot.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wo die Anwendung ihre Daten ablegt.
 *
 * <p>Im Docker-Container wird {@code POSTPILOT_DATA_DIR=/data} gesetzt, damit alles im
 * gemounteten Ordner der NAS landet. Ohne diese Angabe liegt alles im Benutzerprofil,
 * getrennt vom Programm — eine Neuinstallation fasst die eigenen Daten dann nicht an.
 */
public final class AppPaths {

    private static final String APP_FOLDER = "PostPilot";

    private final Path dataDir;

    public AppPaths(String konfigurierterPfad) {
        this.dataDir = (konfigurierterPfad == null || konfigurierterPfad.isBlank())
                ? standardDataDir()
                : Path.of(konfigurierterPfad);
    }

    /** Windows: %APPDATA%\PostPilot — sonst ~/.postpilot */
    public static Path standardDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, APP_FOLDER);
        }
        return Path.of(System.getProperty("user.home"), "." + APP_FOLDER.toLowerCase());
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path stammorteFile() {
        return dataDir.resolve("stammorte.json");
    }

    /** Die hochgeladenen Originalfotos. */
    public Path fotoDir() {
        return dataDir.resolve("fotos");
    }

    /** Fertige Zuschnitte, bereit zum Hochladen. */
    public Path exportDir() {
        return dataDir.resolve("export");
    }

    public void ensureDirectories() throws IOException {
        Files.createDirectories(fotoDir());
        Files.createDirectories(exportDir());
    }
}
