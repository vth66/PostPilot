package de.vothography.postpilot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;
import java.util.UUID;

/**
 * Ein eigener Stammort. Bewusst keine Meta-Orts-ID: der Ort wird beim manuellen
 * Veröffentlichen in Instagram selbst gewählt, die App liefert nur den Namen.
 *
 * @param id            interne ID
 * @param name          Anzeigename, z. B. "Kahler Asten"
 * @param kategorie     freie Einordnung, z. B. "Berg" oder "Altstadt"
 * @param latitude      Breitengrad, optional (null = keine Entfernungsberechnung)
 * @param longitude     Längengrad, optional
 * @param instagramName exakte Schreibweise für die Ortssuche in Instagram, optional
 * @param notiz         eigene Anmerkung, optional
 * @param region        Gegend für die Hashtag-Auswahl: "siegerland", "nrw", "sauerland" oder null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Stammort(
        String id,
        String name,
        String kategorie,
        Double latitude,
        Double longitude,
        String instagramName,
        String notiz,
        String region) {

    public static Stammort neu(String name, String kategorie, Double latitude, Double longitude,
                               String region) {
        return new Stammort(UUID.randomUUID().toString(), name, kategorie, latitude, longitude,
                null, null, region);
    }

    @JsonIgnore
    public Optional<GeoPoint> geoPoint() {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return Optional.of(new GeoPoint(latitude, longitude));
    }

    /** Was beim Veröffentlichen in Instagram eingetippt wird. */
    @JsonIgnore
    public String instagramSuchbegriff() {
        return instagramName == null || instagramName.isBlank() ? name : instagramName;
    }
}
