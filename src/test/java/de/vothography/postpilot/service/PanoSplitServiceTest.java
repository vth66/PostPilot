package de.vothography.postpilot.service;

import de.vothography.postpilot.model.PanoSlice;
import de.vothography.postpilot.model.PanoSplitResult;
import de.vothography.postpilot.model.ProtectedRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft das Bandmodell: Die Ausschnitte nehmen ein waagerechtes Band aus dem Foto und treffen
 * dabei exakt das Zielverhältnis des Carousels — nur so liegen ein 4:5-Opener und ein
 * Pano-Split im selben Post, ohne dass Instagram etwas umformatiert.
 */
class PanoSplitServiceTest {

    private final PanoSplitService service = new PanoSplitService();

    /** Toleranz von einer Pixelzeile, weil die Kanten auf ganze Pixel gerundet werden. */
    private static final double PIXEL = 0.002;

    @Test
    @DisplayName("Ausschnitte schließen pixelgenau aneinander an und decken die volle Breite ab")
    void slicesSindNahtlos() {
        PanoSplitResult result = service.split(6000, 3000, 3);

        assertTrue(result.isSeamless());
        assertEquals(0, result.slices().getFirst().x());
        assertEquals(6000, result.slices().getLast().rightEdge());

        List<PanoSlice> slices = result.slices();
        for (int i = 0; i < slices.size() - 1; i++) {
            assertEquals(slices.get(i).rightEdge(), slices.get(i + 1).x(),
                    "Zwischen Ausschnitt " + (i + 1) + " und " + (i + 2) + " entsteht ein Sprung");
        }
    }

    @Test
    @DisplayName("Ungerade Bildbreiten erzeugen keine Lücke und keine Überlappung")
    void ungeradeBreiteBleibtNahtlos() {
        PanoSplitResult result = service.split(4001, 1400, 4);

        assertTrue(result.isSeamless());
        int summe = result.slices().stream().mapToInt(PanoSlice::width).sum();
        assertEquals(4001, summe);
    }

    @Test
    @DisplayName("Alle Ausschnitte haben dieselbe Höhe und dieselbe Oberkante")
    void bandLiegtFuerAlleAusschnitteGleich() {
        PanoSplitResult result = service.split(6000, 4000, 3, 0.8, 0.5);
        PanoSlice erster = result.slices().getFirst();

        for (PanoSlice slice : result.slices()) {
            assertEquals(erster.y(), slice.y(), "Das Band springt bei Ausschnitt " + slice.index());
            assertEquals(erster.height(), slice.height(), "Ausschnitt " + slice.index() + " ist anders hoch");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6, 7, 8})
    @DisplayName("Jeder Ausschnitt bleibt im erlaubten Seitenverhältnis")
    void seitenverhaeltnisseBleibenGueltig(int anzahl) {
        PanoSplitResult result = service.split(6000, 1800, anzahl);

        for (PanoSlice slice : result.slices()) {
            double aspect = slice.aspectRatio();
            assertTrue(aspect >= PanoSplitService.MIN_SLICE_ASPECT - PIXEL, "Ausschnitt zu schmal: " + aspect);
            assertTrue(aspect <= PanoSplitService.MAX_SLICE_ASPECT + PIXEL, "Ausschnitt zu breit: " + aspect);
        }
    }

    @Test
    @DisplayName("Ein gewöhnliches 3:2-Foto aus der Kamera lässt sich teilen")
    void dreiZweiFotoIstTeilbar() {
        assertTrue(service.isSuitableForPanoSplit(6000, 4000));
        assertEquals(2, service.minSliceCount(6000, 4000));
        assertEquals(3, service.maxSliceCount(6000, 4000));
    }

    @Test
    @DisplayName("Die Ausschnitte treffen das Zielverhältnis exakt — sonst bricht das Carousel")
    void sliceAspektTrifftZielverhaeltnis() {
        for (double ziel : new double[] {0.8, 1.0, 1.25}) {
            int min = service.minSliceCount(6000, 4000, ziel);
            int max = service.maxSliceCount(6000, 4000, ziel);
            for (int anzahl = min; anzahl <= max; anzahl++) {
                PanoSplitResult result = service.split(6000, 4000, anzahl, ziel, 0.5);
                assertEquals(ziel, result.sliceAspect(), PIXEL,
                        "Ziel " + ziel + " bei " + anzahl + " Ausschnitten verfehlt");
            }
        }
    }

    @Test
    @DisplayName("Mindestens die Hälfte der Bildhöhe bleibt erhalten")
    void bandNutztGenugBildhoehe() {
        for (int anzahl = service.minSliceCount(6000, 4000); anzahl <= service.maxSliceCount(6000, 4000); anzahl++) {
            double nutzung = service.split(6000, 4000, anzahl).hoehenNutzung();
            assertTrue(nutzung >= 0.5 - PIXEL, anzahl + " Ausschnitte nutzen nur " + nutzung + " der Höhe");
        }
    }

    @Test
    @DisplayName("Maximale Anzahl folgt der Formel floor(Breite / (Ziel × Höhe × 0,5))")
    void maximaleAnzahlFolgtDerFormel() {
        assertEquals(4, service.maxSliceCount(1600, 900));
        assertEquals(8, service.maxSliceCount(6000, 1800));
        assertEquals(2, service.maxSliceCount(6000, 4000, 1.25));
    }

    @Test
    @DisplayName("Zu wenige Ausschnitte würden das Band aus dem Bild laufen lassen")
    void mindestanzahlHaeltDasBandImBild() {
        assertEquals(5, service.minSliceCount(6000, 1800));
        assertThrows(IllegalArgumentException.class, () -> service.split(6000, 1800, 4));
    }

    @Test
    @DisplayName("Hochformat und Quadrat taugen nicht für einen Pano-Split")
    void nurQuerformatIstGeeignet() {
        assertFalse(service.isSuitableForPanoSplit(1080, 1350));
        assertFalse(service.isSuitableForPanoSplit(4000, 6000));
        assertFalse(service.isSuitableForPanoSplit(1000, 1000));
        assertTrue(service.isSuitableForPanoSplit(6000, 1800));
    }

    @Test
    @DisplayName("Ein Story-Format ist für ein Carousel kein gültiges Ziel")
    void storyFormatIstKeinGueltigesZiel() {
        assertFalse(service.isValidTargetAspect(0.5625));
        assertFalse(service.isSuitableForPanoSplit(6000, 4000, 0.5625));
        assertThrows(IllegalArgumentException.class, () -> service.split(6000, 4000, 2, 0.5625, 0.5));
    }

    @Test
    @DisplayName("Der Fokus verschiebt das Band senkrecht, ohne das Bild zu verlassen")
    void fokusVerschiebtDasBand() {
        PanoSlice oben = service.split(6000, 4000, 2, 0.8, 0.0).slices().getFirst();
        PanoSlice unten = service.split(6000, 4000, 2, 0.8, 1.0).slices().getFirst();

        assertEquals(0, oben.y());
        assertEquals(4000, unten.y() + unten.height());
    }

    @Test
    @DisplayName("Eine Schnittkante mitten im Hauptmotiv wird erkannt")
    void motivSchnittWirdErkannt() {
        PanoSplitResult result = service.split(6000, 1800, 6);
        ProtectedRegion turmspitze = new ProtectedRegion("Turmspitze", 2900, 3100);

        assertTrue(service.cutsProtectedRegion(result, List.of(turmspitze)));
        assertFalse(service.cutsProtectedRegion(result, List.of(new ProtectedRegion("Baum", 100, 400))));
    }

    @Test
    @DisplayName("Bei bedrohtem Motiv werden lieber weniger, dafür breitere Ausschnitte vorgeschlagen")
    void vorschlagWeichtDemMotivAus() {
        // Bei 8 Ausschnitten liegt eine Kante auf 750, bei 7 auf 857 — das Motiv zwingt also auf 7.
        ProtectedRegion motiv = new ProtectedRegion("Turmspitze", 700, 800);

        OptionalInt vorschlag = service.suggestSliceCount(6000, 1800, List.of(motiv));

        assertTrue(vorschlag.isPresent());
        assertEquals(7, vorschlag.getAsInt());
        assertFalse(service.cutsProtectedRegion(
                service.split(6000, 1800, vorschlag.getAsInt()), List.of(motiv)));
    }

    @Test
    @DisplayName("Ohne Schutzbereich wird die größtmögliche Anzahl vorgeschlagen")
    void vorschlagNutztMaximumWennNichtsImWegIst() {
        assertEquals(8, service.suggestSliceCount(6000, 1800, List.of()).orElseThrow());
    }

    @Test
    @DisplayName("Lässt sich das Motiv bei keiner Anzahl schützen, gibt es keinen Vorschlag")
    void keinVorschlagWennJedeKanteSchneidet() {
        ProtectedRegion motiv = new ProtectedRegion("Panorama-Motiv", 500, 5500);

        assertTrue(service.suggestSliceCount(6000, 1800, List.of(motiv)).isEmpty());
    }

    @Test
    @DisplayName("Die Anzahl lässt sich auf den erlaubten Bereich klemmen")
    void anzahlWirdGeklemmt() {
        assertEquals(3, service.klemmeSliceCount(6000, 4000, 99, 0.8).orElseThrow());
        assertEquals(2, service.klemmeSliceCount(6000, 4000, 1, 0.8).orElseThrow());
        assertTrue(service.klemmeSliceCount(4000, 6000, 3, 0.8).isEmpty());
    }

    @Test
    @DisplayName("Unsinnige Eingaben werden abgewiesen")
    void ungueltigeEingabenWerdenAbgewiesen() {
        assertThrows(IllegalArgumentException.class, () -> service.split(0, 1000, 2));
        assertThrows(IllegalArgumentException.class, () -> service.split(6000, 1800, 0));
        assertThrows(IllegalArgumentException.class, () -> service.split(6000, 1800, 99));
        assertThrows(IllegalArgumentException.class, () -> service.split(6000, 1800, 6, 0, 0.5));
    }
}
