package de.vothography.postpilot.service;

import de.vothography.postpilot.model.CropFormat;
import de.vothography.postpilot.model.PanoSlice;
import de.vothography.postpilot.model.PanoSplitResult;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Bindeglied zwischen den Fotodateien und der Zuschnitt-Logik. */
public final class BildService {

    /** Instagram rechnet ohnehin auf 1080 px Breite herunter. */
    private static final int AUSGABE_BREITE = 1080;

    /** Instagram nimmt höchstens zehn Bilder pro Carousel. */
    private static final int MAX_BILDER_IM_CAROUSEL = 10;

    private final FotoStore fotoStore;
    private final CropService cropService;
    private final PanoSplitService panoSplitService;
    private final AppPaths paths;

    public BildService(FotoStore fotoStore, CropService cropService,
                       PanoSplitService panoSplitService, AppPaths paths) {
        this.fotoStore = fotoStore;
        this.cropService = cropService;
        this.panoSplitService = panoSplitService;
        this.paths = paths;
    }

    public record SliceInfo(int index, int x, int y, int breite, int hoehe, double seitenverhaeltnis) {
    }

    public record PanoUebersicht(boolean geeignet, int minAnzahl, int maxAnzahl, int anzahl,
                                 double sliceAspect, double hoehenNutzung, List<SliceInfo> slices) {
    }

    public record ExportErgebnis(String ordner, List<String> dateien) {
    }

    /**
     * Ein Bild des Carousels — in dieser Reihenfolge ausgewertet:
     * <ul>
     *   <li>{@code panoAnzahl} gesetzt: der {@code sliceIndex}-te Ausschnitt eines Pano-Splits.</li>
     *   <li>sonst {@code fuellung} gesetzt: das ganze Foto ins Format eingepasst, der Freiraum
     *       mit „schwarz", „weiss" oder „weich" gefüllt.</li>
     *   <li>sonst: der übliche Zuschnitt um den Fokuspunkt.</li>
     * </ul>
     */
    public record CarouselBild(String id, double fokusX, double fokusY,
                               Integer panoAnzahl, Integer sliceIndex, String fuellung) {
    }

    /**
     * Ein Carousel: alle Bilder bekommen dasselbe Seitenverhältnis, weil Instagram dem
     * Carousel das Format des ersten Bildes aufzwingt und abweichende Bilder umrechnet.
     */
    public record CarouselWunsch(double seitenverhaeltnis, List<CarouselBild> bilder,
                                 String caption, List<String> hashtags, String ort,
                                 String format) {
    }

    /**
     * Begleitzettel für den Export. Ohne ihn liegen im ZIP nur Bilder, und Caption und
     * Hashtags müssten getrennt durch die Gegend getragen werden — genau da gehen sie verloren.
     */
    static String begleitzettel(CarouselWunsch wunsch, int anzahl) {
        StringBuilder sb = new StringBuilder();
        sb.append("PostPilot — Post vom ")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")))
          .append("\n\n");
        sb.append("Format: ").append(wunsch.format() == null ? "–" : wunsch.format())
          .append("  (Seitenverhältnis ").append(String.format(java.util.Locale.GERMANY, "%.4f",
                wunsch.seitenverhaeltnis())).append(")\n");
        sb.append("Bilder: ").append(anzahl).append(" (01.jpg bis ")
          .append(String.format("%02d", anzahl)).append(".jpg, in dieser Reihenfolge posten)\n");
        if (wunsch.ort() != null && !wunsch.ort().isBlank()) {
            sb.append("Ort:    ").append(wunsch.ort()).append("\n");
        }

        sb.append("\n--- CAPTION ---\n");
        sb.append(wunsch.caption() == null || wunsch.caption().isBlank()
                ? "(keine)" : wunsch.caption().trim()).append("\n");

        List<String> tags = wunsch.hashtags() == null ? List.of() : wunsch.hashtags();
        sb.append("\n--- HASHTAGS (").append(tags.size()).append(") ---\n");
        sb.append(tags.isEmpty() ? "(keine)" : String.join(" ", tags)).append("\n");

        sb.append("\n--- CAPTION MIT HASHTAGS, zum Kopieren ---\n");
        String zusammen = (wunsch.caption() == null ? "" : wunsch.caption().trim());
        if (!tags.isEmpty()) {
            zusammen = zusammen.isEmpty() ? String.join(" ", tags)
                    : zusammen + "\n\n" + String.join(" ", tags);
        }
        sb.append(zusammen.isEmpty() ? "(leer)" : zusammen).append("\n");
        return sb.toString();
    }

    public byte[] crop(String fotoId, CropFormat format, double fokusX, double fokusY) throws IOException {
        BufferedImage original = fotoStore.lade(fotoId);
        Rectangle ausschnitt = cropService.computeCrop(
                original.getWidth(), original.getHeight(), format.aspectRatio(), fokusX, fokusY);
        BufferedImage zugeschnitten = cropService.crop(original, ausschnitt);
        BufferedImage skaliert = cropService.resize(zugeschnitten, format.targetWidth(), format.targetHeight());
        return JpegWriter.toBytes(skaliert);
    }

    /** Zuschnitt auf ein frei gewähltes Seitenverhältnis — für Carousels mit gemischten Formaten. */
    public byte[] crop(String fotoId, double seitenverhaeltnis, double fokusX, double fokusY)
            throws IOException {
        return JpegWriter.toBytes(cropBild(fotoId, seitenverhaeltnis, fokusX, fokusY));
    }

    private BufferedImage cropBild(String fotoId, double seitenverhaeltnis, double fokusX, double fokusY)
            throws IOException {
        if (seitenverhaeltnis <= 0) {
            throw new IllegalArgumentException("Seitenverhältnis muss positiv sein");
        }
        BufferedImage original = fotoStore.lade(fotoId);
        Rectangle ausschnitt = cropService.computeCrop(
                original.getWidth(), original.getHeight(), seitenverhaeltnis, fokusX, fokusY);
        BufferedImage zugeschnitten = cropService.crop(original, ausschnitt);
        return cropService.resize(zugeschnitten, AUSGABE_BREITE, ausgabeHoehe(seitenverhaeltnis));
    }

    private static int ausgabeHoehe(double seitenverhaeltnis) {
        return (int) Math.round(AUSGABE_BREITE / seitenverhaeltnis);
    }

    /** Das ganze Foto im Zielformat, mit Freiraum statt Beschnitt. */
    public byte[] ganzesBild(String fotoId, double seitenverhaeltnis, String fuellung)
            throws IOException {
        return JpegWriter.toBytes(ganzesBildIntern(fotoId, seitenverhaeltnis, fuellung));
    }

    private BufferedImage ganzesBildIntern(String fotoId, double seitenverhaeltnis, String fuellung)
            throws IOException {
        if (seitenverhaeltnis <= 0) {
            throw new IllegalArgumentException("Seitenverhältnis muss positiv sein");
        }
        return cropService.einpassen(fotoStore.lade(fotoId), AUSGABE_BREITE,
                ausgabeHoehe(seitenverhaeltnis), CropService.Fuellung.ausText(fuellung));
    }

    /**
     * Schreibt ein Carousel in der übergebenen Reihenfolge; alle Bilder bekommen exakt
     * dieselben Maße, damit Instagram nichts nachträglich umformatiert.
     *
     * <p>Ein Eintrag ist entweder ein ganzes Foto oder ein Ausschnitt aus einem Pano-Split —
     * so liegen ein 4:5-Opener und die Teile eines Panoramas in einem Carousel.
     */
    public ExportErgebnis exportiereCarousel(CarouselWunsch wunsch) throws IOException {
        List<FertigesBild> bilder = carouselBilder(wunsch);

        Path ziel = paths.exportDir().resolve(zeitstempel() + "_carousel");
        Files.createDirectories(ziel);

        List<String> dateien = new ArrayList<>();
        for (FertigesBild bild : bilder) {
            Files.write(ziel.resolve(bild.name()), bild.daten());
            dateien.add(bild.name());
        }
        Files.writeString(ziel.resolve("post.txt"), begleitzettel(wunsch, bilder.size()),
                StandardCharsets.UTF_8);
        dateien.add("post.txt");
        return new ExportErgebnis(ziel.toAbsolutePath().toString(), dateien);
    }

    /** Ein fertiges Bild des Carousels mit seinem Dateinamen. */
    public record FertigesBild(String name, byte[] daten) {
    }

    /**
     * Dasselbe Carousel als ZIP zum Herunterladen. Läuft die Anwendung auf der NAS, nützt
     * ein Ordner dort wenig — gebraucht werden die Dateien auf dem Rechner, von dem aus
     * Metricool bedient wird.
     */
    public byte[] carouselAlsZip(CarouselWunsch wunsch) throws IOException {
        List<FertigesBild> bilder = carouselBilder(wunsch);
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(puffer)) {
            for (FertigesBild bild : bilder) {
                zip.putNextEntry(new ZipEntry(bild.name()));
                zip.write(bild.daten());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("post.txt"));
            zip.write(begleitzettel(wunsch, bilder.size()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return puffer.toByteArray();
    }

    /** Vorgeschlagener Dateiname für das ZIP. */
    public String zipName() {
        return "postpilot_" + zeitstempel() + ".zip";
    }

    /**
     * Rechnet alle Bilder des Carousels aus — Einzelbilder wie Pano-Ausschnitte, in der
     * übergebenen Reihenfolge und alle mit exakt denselben Maßen, damit Instagram nichts
     * nachträglich umformatiert.
     */
    public List<FertigesBild> carouselBilder(CarouselWunsch wunsch) throws IOException {
        if (wunsch.bilder().isEmpty()) {
            throw new IllegalArgumentException("Das Carousel enthält kein Bild");
        }
        double aspekt = wunsch.seitenverhaeltnis();
        if (aspekt <= 0) {
            throw new IllegalArgumentException("Seitenverhältnis muss positiv sein");
        }
        if (wunsch.bilder().size() > MAX_BILDER_IM_CAROUSEL) {
            throw new IllegalArgumentException("Instagram nimmt höchstens " + MAX_BILDER_IM_CAROUSEL
                    + " Bilder pro Carousel, hier sind es " + wunsch.bilder().size());
        }
        int zielHoehe = ausgabeHoehe(aspekt);

        List<FertigesBild> fertige = new ArrayList<>(wunsch.bilder().size());
        int nummer = 1;
        for (CarouselBild bild : wunsch.bilder()) {
            BufferedImage fertig;
            if (bild.panoAnzahl() != null) {
                fertig = panoSliceBild(bild.id(), bild.panoAnzahl(),
                        bild.sliceIndex() == null ? 1 : bild.sliceIndex(), aspekt, bild.fokusY());
            } else if (bild.fuellung() != null && !bild.fuellung().isBlank()) {
                fertig = ganzesBildIntern(bild.id(), aspekt, bild.fuellung());
            } else {
                fertig = cropBild(bild.id(), aspekt, bild.fokusX(), bild.fokusY());
            }
            // Letzte Sicherung: Weicht ein Ausschnitt durch Pixelrundung um eine Zeile ab,
            // wird er hier auf die gemeinsame Größe gebracht.
            if (fertig.getWidth() != AUSGABE_BREITE || fertig.getHeight() != zielHoehe) {
                fertig = cropService.resize(fertig, AUSGABE_BREITE, zielHoehe);
            }
            fertige.add(new FertigesBild(String.format("%02d.jpg", nummer++), JpegWriter.toBytes(fertig)));
        }
        return fertige;
    }

    private static String zeitstempel() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
    }

    public PanoUebersicht panoUebersicht(String fotoId, Integer wunschAnzahl) throws IOException {
        return panoUebersicht(fotoId, wunschAnzahl, PanoSplitService.MIN_SLICE_ASPECT, 0.5);
    }

    public PanoUebersicht panoUebersicht(String fotoId, Integer wunschAnzahl,
                                         double zielAspect, double fokusY) throws IOException {
        var foto = fotoStore.alle().stream()
                .filter(f -> f.id().equals(fotoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unbekanntes Foto: " + fotoId));

        int breite = foto.breite();
        int hoehe = foto.hoehe();
        if (!panoSplitService.isSuitableForPanoSplit(breite, hoehe, zielAspect)) {
            return new PanoUebersicht(false, 0, 0, 0, 0, 0, List.of());
        }
        int max = panoSplitService.maxSliceCount(breite, hoehe, zielAspect);
        int min = panoSplitService.minSliceCount(breite, hoehe, zielAspect);
        int anzahl = wunschAnzahl == null ? Math.min(max, Math.max(min, 3)) : wunschAnzahl;
        anzahl = Math.max(min, Math.min(max, anzahl));

        PanoSplitResult ergebnis = panoSplitService.split(breite, hoehe, anzahl, zielAspect, fokusY);
        List<SliceInfo> slices = ergebnis.slices().stream()
                .map(s -> new SliceInfo(s.index(), s.x(), s.y(), s.width(), s.height(), s.aspectRatio()))
                .toList();
        return new PanoUebersicht(true, min, max, anzahl,
                ergebnis.sliceAspect(), ergebnis.hoehenNutzung(), slices);
    }

    public byte[] panoSlice(String fotoId, int anzahl, int index) throws IOException {
        return panoSlice(fotoId, anzahl, index, PanoSplitService.MIN_SLICE_ASPECT, 0.5);
    }

    public byte[] panoSlice(String fotoId, int anzahl, int index, double zielAspect, double fokusY)
            throws IOException {
        return JpegWriter.toBytes(panoSliceBild(fotoId, anzahl, index, zielAspect, fokusY));
    }

    private BufferedImage panoSliceBild(String fotoId, int anzahl, int index,
                                        double zielAspect, double fokusY) throws IOException {
        BufferedImage original = fotoStore.lade(fotoId);
        PanoSplitResult ergebnis = panoSplitService.split(
                original.getWidth(), original.getHeight(), anzahl, zielAspect, fokusY);
        if (index < 1 || index > ergebnis.sliceCount()) {
            throw new IllegalArgumentException("Ausschnitt " + index + " gibt es nicht");
        }
        List<BufferedImage> teile = panoSplitService.renderSlices(original, ergebnis);
        return aufAusgabegroesse(teile.get(index - 1), ergebnis);
    }

    /**
     * Schreibt die fertigen Bilder in einen Ordner unter {@code export/} — von dort werden sie
     * hochgeladen oder von Hand in Metricool gezogen.
     */
    public ExportErgebnis exportiere(String fotoId, CropFormat format,
                                     double fokusX, double fokusY) throws IOException {
        Path ziel = paths.exportDir().resolve(zeitstempel() + "_" + fotoId.substring(0, 8));
        Files.createDirectories(ziel);

        String name = format.name().toLowerCase() + ".jpg";
        Files.write(ziel.resolve(name), crop(fotoId, format, fokusX, fokusY));
        return new ExportErgebnis(ziel.toAbsolutePath().toString(), List.of(name));
    }

    /** Ein einzelnes Bild als ZIP — derselbe Weg wie beim Carousel, nur mit einer Datei. */
    public byte[] alsZip(String fotoId, CropFormat format, double fokusX, double fokusY)
            throws IOException {
        byte[] daten = crop(fotoId, format, fokusX, fokusY);
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(puffer)) {
            zip.putNextEntry(new ZipEntry(format.name().toLowerCase() + ".jpg"));
            zip.write(daten);
            zip.closeEntry();
        }
        return puffer.toByteArray();
    }

    /**
     * Alle Ausschnitte bekommen exakt dieselben Maße — abgeleitet aus dem ersten Ausschnitt.
     * Instagram zwingt einem Carousel das Seitenverhältnis des ersten Bildes auf; ein um ein
     * Pixel abweichender Ausschnitt würde umformatiert und der nahtlose Übergang wäre dahin.
     */
    private BufferedImage aufAusgabegroesse(BufferedImage slice, PanoSplitResult ergebnis) {
        PanoSlice erster = ergebnis.slices().getFirst();
        int zielHoehe = (int) Math.round(AUSGABE_BREITE * (double) erster.height() / erster.width());
        return cropService.resize(slice, AUSGABE_BREITE, zielHoehe);
    }
}
