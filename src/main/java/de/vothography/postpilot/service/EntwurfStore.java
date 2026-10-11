package de.vothography.postpilot.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sichert den Arbeitsstand eines Posts: gewähltes Format, Zuschnitt und Spielart je Foto,
 * Caption, Hashtags, Ort.
 *
 * <p>Vorher lebte das alles nur im Browser. Ein F5, ein Klick auf einen Link und zurück —
 * und sämtliche Einstellungen waren weg, ohne dass es jemand merkte. Der Export lieferte
 * danach brav mittige Standardzuschnitte statt der eingestellten Ausschnitte. Deshalb liegt
 * der Entwurf jetzt neben den Fotos im Datenverzeichnis.
 *
 * <p>Der Inhalt wird bewusst <em>nicht</em> ausgewertet: Der Server speichert die Zeichenkette,
 * wie der Browser sie schickt, und gibt sie unverändert zurück. So muss das Format nicht an
 * zwei Stellen gepflegt werden, wenn in der Oberfläche eine Einstellung dazukommt.
 */
public final class EntwurfStore {

    /** Großzügig bemessen, aber nicht unbegrenzt — die Datei kommt aus dem Browser. */
    private static final int MAX_ZEICHEN = 256 * 1024;

    private static final String LEER = "{}";

    private final AppPaths paths;

    public EntwurfStore(AppPaths paths) {
        this.paths = paths;
    }

    private Path datei() {
        return paths.dataDir().resolve("entwurf.json");
    }

    /** Der gespeicherte Entwurf, oder ein leeres Objekt, wenn es noch keinen gibt. */
    public String lies() throws IOException {
        Path datei = datei();
        if (Files.notExists(datei)) {
            return LEER;
        }
        String inhalt = Files.readString(datei, StandardCharsets.UTF_8).trim();
        return inhalt.isEmpty() ? LEER : inhalt;
    }

    public void schreibe(String inhalt) throws IOException {
        String sauber = inhalt == null ? "" : inhalt.trim();
        if (sauber.isEmpty()) {
            loesche();
            return;
        }
        if (sauber.length() > MAX_ZEICHEN) {
            throw new IllegalArgumentException("Der Entwurf ist zu groß: " + sauber.length()
                    + " Zeichen, erlaubt sind " + MAX_ZEICHEN);
        }
        if (!sauber.startsWith("{")) {
            throw new IllegalArgumentException("Der Entwurf muss ein JSON-Objekt sein");
        }
        Files.createDirectories(paths.dataDir());
        Files.writeString(datei(), sauber, StandardCharsets.UTF_8);
    }

    /** Wird beim „Neuer Post" und nach dem Archivieren aufgerufen. */
    public void loesche() throws IOException {
        Files.deleteIfExists(datei());
    }
}
