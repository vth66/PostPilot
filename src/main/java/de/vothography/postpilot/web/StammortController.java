package de.vothography.postpilot.web;

import de.vothography.postpilot.model.GeoPoint;
import de.vothography.postpilot.model.Stammort;
import de.vothography.postpilot.service.FotoStore;
import de.vothography.postpilot.service.StammortStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Die eigene Ortsliste. Keine Meta-Ortssuche — der Ort wird beim Veröffentlichen
 * in Instagram selbst gewählt, die App merkt ihn sich nur und zeigt ihn an.
 */
@RestController
@RequestMapping("/api/stammorte")
public class StammortController {

    private final StammortStore stammorte;
    private final FotoStore fotoStore;

    public StammortController(StammortStore stammorte, FotoStore fotoStore) {
        this.stammorte = stammorte;
        this.fotoStore = fotoStore;
    }

    public record OrtAnsicht(String id, String name, String kategorie, String instagramName,
                             String region, Double entfernungKm) {
    }

    public record NeuerOrt(String name, String kategorie, Double latitude, Double longitude,
                           String instagramName, String notiz, String region) {
    }

    /**
     * Ohne Suchbegriff und mit einem Foto, das GPS-Daten mitbringt, kommen die nächstgelegenen
     * Orte zuerst — sonst die Treffer der Freitextsuche.
     */
    @GetMapping
    public List<OrtAnsicht> liste(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String fotoId) throws IOException {
        Optional<GeoPoint> aufnahmeort = fotoId == null ? Optional.empty() : fotoStore.aufnahmeort(fotoId);
        List<Stammort> treffer = stammorte.suche(q);

        if (aufnahmeort.isPresent()) {
            GeoPoint punkt = aufnahmeort.get();
            return treffer.stream()
                    .sorted(Comparator.comparingDouble(
                            ort -> stammorte.entfernungKm(ort, punkt).orElse(Double.MAX_VALUE)))
                    .map(ort -> ansicht(ort, stammorte.entfernungKm(ort, punkt).orElse(null)))
                    .toList();
        }
        return treffer.stream().map(ort -> ansicht(ort, null)).toList();
    }

    @PostMapping
    public OrtAnsicht anlegen(@RequestBody NeuerOrt neuerOrt) throws IOException {
        Stammort ort = new Stammort(
                java.util.UUID.randomUUID().toString(),
                neuerOrt.name(),
                neuerOrt.kategorie() == null ? "Eigener Ort" : neuerOrt.kategorie(),
                neuerOrt.latitude(),
                neuerOrt.longitude(),
                neuerOrt.instagramName(),
                neuerOrt.notiz(),
                neuerOrt.region());
        stammorte.hinzufuegen(ort);
        return ansicht(ort, null);
    }

    @DeleteMapping("/{id}")
    public void entfernen(@PathVariable String id) throws IOException {
        stammorte.entfernen(id);
    }

    private OrtAnsicht ansicht(Stammort ort, Double entfernungKm) {
        return new OrtAnsicht(ort.id(), ort.name(), ort.kategorie(),
                ort.instagramSuchbegriff(), ort.region(), entfernungKm);
    }
}
