package de.vothography.postpilot.service;

import de.vothography.postpilot.model.PanoSlice;
import de.vothography.postpilot.model.PanoSplitResult;
import de.vothography.postpilot.model.ProtectedRegion;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

/**
 * Teilt ein Landschaftsfoto in mehrere Ausschnitte für ein Instagram-Carousel.
 *
 * <p>Die Ausschnitte nehmen ein waagerechtes Band aus dem Foto — nicht zwingend die volle
 * Bildhöhe. Genau daran scheiterte die erste Fassung: Über die volle Höhe gerechnet braucht
 * ein Pano-Split ein Seitenverhältnis ab 1,6, womit ein gewöhnliches 3:2-Foto aus der Kamera
 * durchfiel. Mit dem Band lässt sich fast jedes Querformat teilen; der Preis ist etwas
 * Bildhöhe, und wie viel genau, sagt {@link PanoSplitResult#hoehenNutzung()}.
 *
 * <p>Jede Rechnung hier bezieht sich auf ein <em>Zielverhältnis</em> — das Format des
 * Carousels. Instagram zwingt allen Bildern eines Carousels das Format des ersten Bildes auf,
 * also müssen die Ausschnitte exakt dasselbe Verhältnis haben wie ein einzeln eingestelltes
 * Foto daneben. Darum ist die kleinste erlaubte Anzahl so gewählt, dass das Band in die
 * Bildhöhe passt: nur dann trifft der Ausschnitt das Zielverhältnis genau und ein 4:5-Opener
 * und ein Pano-Split liegen ohne Nachformatieren im selben Carousel.
 *
 * <p>Die übrigen Regeln bleiben:
 * <ul>
 *   <li>Die Ausschnitte schließen pixelgenau aneinander an — keine Überlappung, keine Lücke.</li>
 *   <li>Alle Ausschnitte haben exakt dieselben Maße, sonst formatiert Instagram das Carousel um.</li>
 *   <li>Das Zielverhältnis selbst muss zwischen 0,8 und 1,91 liegen — Instagrams Grenzen.</li>
 *   <li>Läuft eine Schnittkante durch das Hauptmotiv, lieber weniger und breitere Ausschnitte.</li>
 * </ul>
 */
public final class PanoSplitService {

    /** 4:5 — das schmalste von Instagram akzeptierte Hochformat. */
    public static final double MIN_SLICE_ASPECT = 0.8;

    /** Grenze zum Querformat. */
    public static final double MAX_SLICE_ASPECT = 1.91;

    /** Unter die Hälfte der Bildhöhe zu gehen lohnt sich nicht mehr — dann ist zu viel weg. */
    private static final double MIN_HOEHEN_NUTZUNG = 0.5;

    /**
     * Ein Panorama ist eine waagerechte Angelegenheit: Das Foto muss breiter als hoch sein.
     * Rein rechnerisch ließe sich auch ein Hochformat in schmale Streifen schneiden — das wäre
     * aber kein Panorama, sondern Konfetti, und die Oberfläche würde es fälschlich anbieten.
     */
    private static final double MIN_QUELL_ASPECT = 1.0;

    /** Instagram nimmt höchstens zehn Bilder pro Carousel. */
    private static final int MAX_BILDER_IM_CAROUSEL = 10;

    private static final double EPS = 1e-9;

    /** Taugt das Zielverhältnis überhaupt für ein Instagram-Carousel? */
    public boolean isValidTargetAspect(double zielAspect) {
        return zielAspect >= MIN_SLICE_ASPECT - EPS && zielAspect <= MAX_SLICE_ASPECT + EPS;
    }

    /** Wie {@link #minSliceCount(int, int, double)} für ein 4:5-Carousel. */
    public int minSliceCount(int breite, int hoehe) {
        return minSliceCount(breite, hoehe, MIN_SLICE_ASPECT);
    }

    /**
     * Wenigste Ausschnitte: So viele, dass das Band noch in die Bildhöhe passt. Bei weniger
     * wäre der Ausschnitt breiter als das Zielverhältnis — und damit anders als der Rest
     * des Carousels.
     */
    public int minSliceCount(int breite, int hoehe, double zielAspect) {
        requirePositiveDimensions(breite, hoehe);
        requireValidTargetAspect(zielAspect);
        return Math.max(2, (int) Math.ceil(breite / (zielAspect * hoehe) - EPS));
    }

    /** Wie {@link #maxSliceCount(int, int, double)} für ein 4:5-Carousel. */
    public int maxSliceCount(int breite, int hoehe) {
        return maxSliceCount(breite, hoehe, MIN_SLICE_ASPECT);
    }

    /**
     * Meiste Ausschnitte: Je mehr, desto schmaler das Band und desto mehr Bildhöhe fällt weg.
     * Begrenzt wird bei der Hälfte der Bildhöhe und bei zehn Bildern.
     *
     * <p>Bewusst ohne Untergrenze: Liegt das Ergebnis unter {@link #minSliceCount}, gibt es
     * keine Anzahl, die Zielverhältnis und genug Bildhöhe zugleich erfüllt — dann ist das Foto
     * für diesen Pano-Split nicht geeignet (etwa ein Hochformat).
     */
    public int maxSliceCount(int breite, int hoehe, double zielAspect) {
        requirePositiveDimensions(breite, hoehe);
        requireValidTargetAspect(zielAspect);
        int ausHoehe = (int) Math.floor(breite / (zielAspect * hoehe * MIN_HOEHEN_NUTZUNG) + EPS);
        return Math.min(MAX_BILDER_IM_CAROUSEL, ausHoehe);
    }

    public boolean isSuitableForPanoSplit(int breite, int hoehe) {
        return isSuitableForPanoSplit(breite, hoehe, MIN_SLICE_ASPECT);
    }

    public boolean isSuitableForPanoSplit(int breite, int hoehe, double zielAspect) {
        requirePositiveDimensions(breite, hoehe);
        return isValidTargetAspect(zielAspect)
                && (double) breite / hoehe > MIN_QUELL_ASPECT + EPS
                && minSliceCount(breite, hoehe, zielAspect) <= maxSliceCount(breite, hoehe, zielAspect);
    }

    public boolean isValidSliceCount(int breite, int hoehe, int sliceCount) {
        return isValidSliceCount(breite, hoehe, sliceCount, MIN_SLICE_ASPECT);
    }

    public boolean isValidSliceCount(int breite, int hoehe, int sliceCount, double zielAspect) {
        return isSuitableForPanoSplit(breite, hoehe, zielAspect)
                && sliceCount >= minSliceCount(breite, hoehe, zielAspect)
                && sliceCount <= maxSliceCount(breite, hoehe, zielAspect);
    }

    /** Hält die Anzahl in den erlaubten Bereich — oder {@code empty}, wenn es keinen gibt. */
    public OptionalInt klemmeSliceCount(int breite, int hoehe, int wunsch, double zielAspect) {
        if (!isSuitableForPanoSplit(breite, hoehe, zielAspect)) {
            return OptionalInt.empty();
        }
        int min = minSliceCount(breite, hoehe, zielAspect);
        int max = maxSliceCount(breite, hoehe, zielAspect);
        return OptionalInt.of(Math.max(min, Math.min(max, wunsch)));
    }

    /** Zerlegt mit mittig sitzendem Band im 4:5-Format. */
    public PanoSplitResult split(int breite, int hoehe, int sliceCount) {
        return split(breite, hoehe, sliceCount, MIN_SLICE_ASPECT, 0.5);
    }

    /**
     * Zerlegt die Bildbreite in {@code sliceCount} gleich breite Ausschnitte.
     *
     * @param zielAspect Seitenverhältnis je Ausschnitt — das Format des Carousels
     * @param fokusY     0,0 = Band am oberen Rand, 0,5 = mittig, 1,0 = am unteren Rand
     */
    public PanoSplitResult split(int breite, int hoehe, int sliceCount, double zielAspect, double fokusY) {
        requirePositiveDimensions(breite, hoehe);
        requireValidTargetAspect(zielAspect);
        if (!isValidSliceCount(breite, hoehe, sliceCount, zielAspect)) {
            throw new IllegalArgumentException(
                    "Ungültige Anzahl Ausschnitte: " + sliceCount + " (erlaubt: "
                            + minSliceCount(breite, hoehe, zielAspect) + " bis "
                            + maxSliceCount(breite, hoehe, zielAspect) + ")");
        }

        // Alle Ausschnitte bekommen dieselbe Höhe — sonst formatiert Instagram das Carousel um.
        int sliceBreite = (int) Math.round((double) breite / sliceCount);
        int bandHoehe = Math.min(hoehe, (int) Math.round(sliceBreite / zielAspect));
        int bandOben = klemme((int) Math.round(fokusY * hoehe - bandHoehe / 2.0), 0, hoehe - bandHoehe);

        List<PanoSlice> slices = new ArrayList<>(sliceCount);
        int vorherigeKante = 0;
        for (int i = 1; i <= sliceCount; i++) {
            int kante = (i == sliceCount) ? breite : (int) Math.round((double) breite * i / sliceCount);
            slices.add(new PanoSlice(i, vorherigeKante, bandOben, kante - vorherigeKante, bandHoehe));
            vorherigeKante = kante;
        }

        PanoSplitResult result = new PanoSplitResult(slices, breite, hoehe);
        verifyAspects(result);
        return result;
    }

    /** Läuft eine der inneren Schnittkanten durch ein geschütztes Motiv? */
    public boolean cutsProtectedRegion(PanoSplitResult result, Collection<ProtectedRegion> protectedRegions) {
        for (int boundary : result.innerBoundaries()) {
            for (ProtectedRegion region : protectedRegions) {
                if (region.contains(boundary)) {
                    return true;
                }
            }
        }
        return false;
    }

    public OptionalInt suggestSliceCount(int breite, int hoehe, Collection<ProtectedRegion> protectedRegions) {
        return suggestSliceCount(breite, hoehe, protectedRegions, MIN_SLICE_ASPECT);
    }

    /**
     * Sucht die größte Anzahl Ausschnitte, die kein geschütztes Motiv zerschneidet — von oben
     * nach unten, sodass im Zweifel weniger und breitere Ausschnitte herauskommen.
     */
    public OptionalInt suggestSliceCount(int breite, int hoehe,
                                         Collection<ProtectedRegion> protectedRegions, double zielAspect) {
        if (!isSuitableForPanoSplit(breite, hoehe, zielAspect)) {
            return OptionalInt.empty();
        }
        int min = minSliceCount(breite, hoehe, zielAspect);
        for (int anzahl = maxSliceCount(breite, hoehe, zielAspect); anzahl >= min; anzahl--) {
            if (!cutsProtectedRegion(split(breite, hoehe, anzahl, zielAspect, 0.5), protectedRegions)) {
                return OptionalInt.of(anzahl);
            }
        }
        return OptionalInt.empty();
    }

    /** Schneidet die Ausschnitte tatsächlich aus dem Bild heraus. */
    public List<BufferedImage> renderSlices(BufferedImage source, PanoSplitResult result) {
        if (source.getWidth() != result.sourceWidth() || source.getHeight() != result.sourceHeight()) {
            throw new IllegalArgumentException("Bildgröße passt nicht zum Split-Ergebnis");
        }
        List<BufferedImage> images = new ArrayList<>(result.sliceCount());
        for (PanoSlice slice : result.slices()) {
            BufferedImage part = new BufferedImage(slice.width(), slice.height(), BufferedImage.TYPE_INT_RGB);
            part.createGraphics().drawImage(
                    source.getSubimage(slice.x(), slice.y(), slice.width(), slice.height()), 0, 0, null);
            images.add(part);
        }
        return images;
    }

    private void verifyAspects(PanoSplitResult result) {
        for (PanoSlice slice : result.slices()) {
            double aspect = slice.aspectRatio();
            // Eine Pixelbreite Toleranz, damit ganzzahlige Kanten nicht knapp durchfallen.
            double toleranz = 1.0 / slice.height();
            if (aspect < MIN_SLICE_ASPECT - toleranz || aspect > MAX_SLICE_ASPECT + toleranz) {
                throw new IllegalStateException("Ausschnitt " + slice.index()
                        + " liegt außerhalb der erlaubten Seitenverhältnisse: " + aspect);
            }
        }
    }

    private static int klemme(int wert, int min, int max) {
        return Math.max(min, Math.min(max, wert));
    }

    private void requireValidTargetAspect(double zielAspect) {
        if (zielAspect <= 0) {
            throw new IllegalArgumentException("Seitenverhältnis muss positiv sein");
        }
        if (!isValidTargetAspect(zielAspect)) {
            throw new IllegalArgumentException("Instagram erlaubt nur Seitenverhältnisse zwischen "
                    + MIN_SLICE_ASPECT + " und " + MAX_SLICE_ASPECT + ", nicht " + zielAspect);
        }
    }

    private void requirePositiveDimensions(int breite, int hoehe) {
        if (breite <= 0 || hoehe <= 0) {
            throw new IllegalArgumentException("Bildmaße müssen positiv sein: " + breite + "x" + hoehe);
        }
    }
}
