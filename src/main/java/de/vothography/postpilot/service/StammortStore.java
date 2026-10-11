package de.vothography.postpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import de.vothography.postpilot.model.GeoPoint;
import de.vothography.postpilot.model.Stammort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Verwaltet die eigene Ortsliste als JSON-Datei im Benutzerprofil.
 * Bewusst keine Datenbank: eine Handvoll Orte, die man auch im Editor öffnen können soll.
 */
public final class StammortStore {

    private static final String DEFAULT_RESOURCE = "/de/vothography/postpilot/stammorte-default.json";

    private final Path file;
    private final ObjectMapper mapper;
    private final List<Stammort> orte = new ArrayList<>();

    public StammortStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Lädt die Liste; fehlt die Datei, wird sie mit den mitgelieferten Beispielorten angelegt. */
    public void load() throws IOException {
        orte.clear();
        if (Files.notExists(file)) {
            orte.addAll(readDefaults());
            save();
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            orte.addAll(mapper.readValue(in, new TypeReference<List<Stammort>>() {
            }));
        }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), orte);
    }

    public List<Stammort> alle() {
        return List.copyOf(orte);
    }

    /** Freitextsuche über Name und Kategorie. */
    public List<Stammort> suche(String query) {
        if (query == null || query.isBlank()) {
            return alle();
        }
        String needle = query.trim().toLowerCase(Locale.GERMAN);
        return orte.stream()
                .filter(ort -> ort.name().toLowerCase(Locale.GERMAN).contains(needle)
                        || (ort.kategorie() != null && ort.kategorie().toLowerCase(Locale.GERMAN).contains(needle)))
                .toList();
    }

    /** Die nächstgelegenen Orte zum Aufnahmepunkt des Fotos, sortiert nach Luftlinie. */
    public List<Stammort> naechste(GeoPoint aufnahmeort, int limit) {
        return orte.stream()
                .filter(ort -> ort.geoPoint().isPresent())
                .sorted(Comparator.comparingDouble(ort -> ort.geoPoint().orElseThrow().distanceKmTo(aufnahmeort)))
                .limit(limit)
                .toList();
    }

    public Optional<Double> entfernungKm(Stammort ort, GeoPoint aufnahmeort) {
        return ort.geoPoint().map(punkt -> punkt.distanceKmTo(aufnahmeort));
    }

    public Stammort hinzufuegen(Stammort ort) throws IOException {
        orte.add(ort);
        save();
        return ort;
    }

    public boolean entfernen(String id) throws IOException {
        boolean removed = orte.removeIf(ort -> ort.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public Path file() {
        return file;
    }

    private List<Stammort> readDefaults() throws IOException {
        try (InputStream in = StammortStore.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return List.of();
            }
            return mapper.readValue(in, new TypeReference<List<Stammort>>() {
            });
        }
    }
}
