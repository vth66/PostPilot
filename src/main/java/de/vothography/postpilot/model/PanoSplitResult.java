package de.vothography.postpilot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ergebnis eines Pano-Splits: lückenlose, überlappungsfreie Ausschnitte über die volle Bildbreite.
 */
public record PanoSplitResult(List<PanoSlice> slices, int sourceWidth, int sourceHeight) {

    public PanoSplitResult {
        slices = List.copyOf(slices);
    }

    public int sliceCount() {
        return slices.size();
    }

    /** Das tatsächliche Seitenverhältnis der Ausschnitte — maßgeblich für das ganze Carousel. */
    public double sliceAspect() {
        return slices.isEmpty() ? 0 : slices.getFirst().aspectRatio();
    }

    /** Anteil der genutzten Bildhöhe: 1,0 = volle Höhe, 0,7 = ein Band über 70 Prozent. */
    public double hoehenNutzung() {
        return slices.isEmpty() ? 0 : (double) slices.getFirst().height() / sourceHeight;
    }

    /** Die inneren Schnittkanten (ohne 0 und ohne Bildbreite) — dort wird das Motiv zerschnitten. */
    public List<Integer> innerBoundaries() {
        List<Integer> boundaries = new ArrayList<>(Math.max(0, slices.size() - 1));
        for (int i = 0; i < slices.size() - 1; i++) {
            boundaries.add(slices.get(i).rightEdge());
        }
        return List.copyOf(boundaries);
    }

    /** Prüft die Zusicherung „pixelgenau aneinander, keine Überlappung, volle Breite". */
    public boolean isSeamless() {
        if (slices.isEmpty()) {
            return false;
        }
        int expectedStart = 0;
        PanoSlice erster = slices.getFirst();
        for (PanoSlice slice : slices) {
            if (slice.x() != expectedStart) {
                return false;
            }
            // Gleiche Höhe und gleiche Oberkante: sonst springt das Band beim Durchwischen.
            if (slice.height() != erster.height() || slice.y() != erster.y()) {
                return false;
            }
            expectedStart = slice.rightEdge();
        }
        return expectedStart == sourceWidth;
    }
}
