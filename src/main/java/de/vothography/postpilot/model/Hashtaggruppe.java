package de.vothography.postpilot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Eine Kategorie der Hashtag-Bank.
 *
 * @param id     technischer Schlüssel, etwa "siegerland"
 * @param name   Anzeigename
 * @param typ    steuert, wie viele Tags die Vorschlagsregel aus dieser Gruppe nimmt
 * @param region nur bei {@link Typ#REGIONAL}: auf welche Gegend sich die Gruppe bezieht
 * @param tags   die Tags in bewusster Reihenfolge — die vordersten werden zuerst vorgeschlagen
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Hashtaggruppe(String id, String name, Typ typ, String region, List<String> tags) {

    public enum Typ {
        /** Bei praktisch jedem Post sinnvoll. */
        CORE,
        /** Nur bei echtem lokalem Bezug. */
        REGIONAL,
        /** Presse- und Magazin-Tags, nur bei besonders starken Motiven. */
        PRESSE,
        /** Passend zum Motiv auszuwählen. */
        GENRE,
        /** Generische Reichweite, sparsam einsetzen. */
        REICHWEITE
    }

    public Hashtaggruppe {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
