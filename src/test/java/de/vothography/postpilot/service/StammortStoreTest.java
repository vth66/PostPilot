package de.vothography.postpilot.service;

import de.vothography.postpilot.model.GeoPoint;
import de.vothography.postpilot.model.Stammort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StammortStoreTest {

    @Test
    @DisplayName("Fehlt die Datei, wird sie mit den mitgelieferten Orten angelegt")
    void legtDateiMitBeispielortenAn(@TempDir Path tmp) throws IOException {
        Path datei = tmp.resolve("stammorte.json");
        StammortStore store = new StammortStore(datei);

        store.load();

        assertTrue(Files.exists(datei));
        assertFalse(store.alle().isEmpty());
    }

    @Test
    @DisplayName("Gespeicherte Orte werden wieder eingelesen")
    void speichernUndLadenBleibtStabil(@TempDir Path tmp) throws IOException {
        Path datei = tmp.resolve("stammorte.json");
        StammortStore store = new StammortStore(datei);
        store.load();
        int vorher = store.alle().size();

        Stammort neu = Stammort.neu("Hoher Wald", "Aussichtspunkt", 50.91, 7.88, "siegerland");
        store.hinzufuegen(neu);

        StammortStore neuGeladen = new StammortStore(datei);
        neuGeladen.load();

        assertEquals(vorher + 1, neuGeladen.alle().size());
        assertTrue(neuGeladen.alle().stream().anyMatch(ort -> ort.name().equals("Hoher Wald")));
    }

    @Test
    @DisplayName("Die Suche findet Orte unabhängig von Groß- und Kleinschreibung")
    void sucheIgnoriertSchreibweise(@TempDir Path tmp) throws IOException {
        StammortStore store = new StammortStore(tmp.resolve("stammorte.json"));
        store.load();

        List<Stammort> treffer = store.suche("kahler");

        assertEquals(1, treffer.size());
        assertEquals("Kahler Asten", treffer.getFirst().name());
    }

    @Test
    @DisplayName("Die nächsten Orte werden nach Luftlinie sortiert")
    void naechsteOrteSindNachEntfernungSortiert(@TempDir Path tmp) throws IOException {
        StammortStore store = new StammortStore(tmp.resolve("stammorte.json"));
        store.load();
        GeoPoint freudenberg = new GeoPoint(50.9003, 7.8756);

        List<Stammort> naechste = store.naechste(freudenberg, 3);

        assertEquals("Alter Flecken, Freudenberg", naechste.getFirst().name());
        double ersteEntfernung = store.entfernungKm(naechste.get(0), freudenberg).orElseThrow();
        double zweiteEntfernung = store.entfernungKm(naechste.get(1), freudenberg).orElseThrow();
        assertTrue(ersteEntfernung <= zweiteEntfernung);
    }

    @Test
    @DisplayName("Ein Ort ohne eigene Instagram-Schreibweise nutzt seinen Namen")
    void instagramSuchbegriffFaelltAufDenNamenZurueck() {
        Stammort ohne = Stammort.neu("Gillerturm", "Aussichtsturm", null, null, "siegerland");
        assertEquals("Gillerturm", ohne.instagramSuchbegriff());

        Stammort mit = new Stammort("1", "Alter Flecken", "Altstadt", null, null,
                "Alter Flecken Freudenberg", null, "siegerland");
        assertEquals("Alter Flecken Freudenberg", mit.instagramSuchbegriff());
    }
}
