package de.vothography.postpilot.model;

import java.util.List;

/**
 * Ein abgeschlossener Post im Archiv.
 *
 * <p>Was hier steht, liegt als {@code post.json} im Ordner des Posts. Der Sinn des Archivs
 * ist nicht die Sicherung der Originale — die liegen ohnehin in der Fotoverwaltung —, sondern
 * die Frage „was habe ich damals getextet und getaggt?". Deshalb wandern Caption, Hashtags,
 * Ort und Reihenfolge mit ins Archiv, nicht nur Bilddateien.
 */
public record ArchivPost(
        String id,
        String name,
        String erstellt,
        String format,
        double seitenverhaeltnis,
        String caption,
        List<String> hashtags,
        String ort,
        List<Bild> bilder,
        List<String> originale) {

    /**
     * Ein Bild des Posts: {@code datei} ist das fertige JPEG im Archivordner,
     * {@code herkunft} die Datei, aus der es entstanden ist.
     */
    public record Bild(String datei, String herkunft, String art, Integer sliceIndex, Integer anzahl) {
    }

    public ArchivPost {
        hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
        bilder = bilder == null ? List.of() : List.copyOf(bilder);
        originale = originale == null ? List.of() : List.copyOf(originale);
    }

    public boolean hatOriginale() {
        return !originale.isEmpty();
    }
}
