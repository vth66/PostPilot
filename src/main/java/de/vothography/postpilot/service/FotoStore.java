package de.vothography.postpilot.service;

import de.vothography.postpilot.model.GeoPoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Verwaltet die hochgeladenen Fotos als Dateien im Datenverzeichnis.
 * Bewusst ohne Datenbank — ein Ordner mit Bildern ist auch von Hand durchschaubar.
 */
public final class FotoStore {

    private static final List<String> ERLAUBTE_ENDUNGEN = List.of("jpg", "jpeg", "png");

    private final AppPaths paths;
    private final ExifService exifService;
    private final ObjectMapper mapper = new ObjectMapper();

    public FotoStore(AppPaths paths, ExifService exifService) throws IOException {
        this.paths = paths;
        this.exifService = exifService;
        paths.ensureDirectories();
    }

    private Path reihenfolgeDatei() {
        return paths.dataDir().resolve("reihenfolge.json");
    }

    /** Die gespeicherte Reihenfolge der Fotos; unbekannte Fotos hängen hinten an. */
    public List<String> reihenfolge() throws IOException {
        if (Files.notExists(reihenfolgeDatei())) {
            return List.of();
        }
        try (InputStream in = Files.newInputStream(reihenfolgeDatei())) {
            return mapper.readValue(in, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Legt die Reihenfolge fest — sie bestimmt, in welcher Folge das Carousel erscheint. */
    public void setzeReihenfolge(List<String> ids) throws IOException {
        Files.createDirectories(paths.dataDir());
        mapper.writeValue(reihenfolgeDatei().toFile(), List.copyOf(ids));
    }

    /** Ein Foto mit allem, was die Oberfläche darüber wissen muss. */
    public record FotoInfo(String id, String dateiname, int breite, int hoehe,
                           Double latitude, Double longitude) {

        public boolean hatGps() {
            return latitude != null && longitude != null;
        }
    }

    public FotoInfo speichern(InputStream daten, String originalName) throws IOException {
        String endung = endungVon(originalName);
        if (!ERLAUBTE_ENDUNGEN.contains(endung)) {
            throw new IllegalArgumentException("Nur JPG und PNG werden unterstützt, nicht: " + endung);
        }
        String id = UUID.randomUUID().toString();
        Path ziel = paths.fotoDir().resolve(id + "." + endung);
        Files.copy(daten, ziel, StandardCopyOption.REPLACE_EXISTING);
        return lese(ziel).orElseThrow(() -> new IOException("Das Foto konnte nicht gelesen werden"));
    }

    /**
     * Legt eine zweite, unabhängige Kopie des Fotos an und sortiert sie direkt dahinter ein.
     *
     * <p>Gedacht für den Ablauf „ganzes Panorama als Auftakt, danach dasselbe Foto als
     * Pano-Split": Beide Male dasselbe Bild, aber mit eigener Einstellung — und dafür braucht
     * es zwei Einträge im Carousel.
     */
    public FotoInfo duplizieren(String id) throws IOException {
        Path quelle = pfad(id).orElseThrow(() -> new IllegalArgumentException("Unbekanntes Foto: " + id));

        String endung = endungVon(quelle.getFileName().toString());
        String neueId = UUID.randomUUID().toString();
        Path kopie = paths.fotoDir().resolve(neueId + "." + endung);
        Files.copy(quelle, kopie);

        // Die Kopie gehört direkt hinter das Original, nicht ans Ende der Leiste.
        List<String> neueReihenfolge = new ArrayList<>(alle().stream().map(FotoInfo::id).toList());
        neueReihenfolge.remove(neueId);
        int hinter = neueReihenfolge.indexOf(id);
        neueReihenfolge.add(hinter < 0 ? neueReihenfolge.size() : hinter + 1, neueId);
        setzeReihenfolge(neueReihenfolge);

        return lese(kopie).orElseThrow(() -> new IOException("Die Kopie konnte nicht gelesen werden"));
    }

    public List<FotoInfo> alle() throws IOException {
        List<FotoInfo> fotos = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.fotoDir())) {
            for (Path datei : stream) {
                lese(datei).ifPresent(fotos::add);
            }
        }
        List<String> reihenfolge = reihenfolge();
        // Fotos ohne Eintrag in der Reihenfolge landen hinten, untereinander nach Dateiname.
        fotos.sort(Comparator
                .comparingInt((FotoInfo f) -> {
                    int index = reihenfolge.indexOf(f.id());
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparing(FotoInfo::dateiname));
        return fotos;
    }

    public Optional<Path> pfad(String id) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.fotoDir(), id + ".*")) {
            Iterator<Path> treffer = stream.iterator();
            return treffer.hasNext() ? Optional.of(treffer.next()) : Optional.empty();
        }
    }

    public BufferedImage lade(String id) throws IOException {
        Path datei = pfad(id).orElseThrow(() -> new IllegalArgumentException("Unbekanntes Foto: " + id));
        BufferedImage bild = ImageIO.read(datei.toFile());
        if (bild == null) {
            throw new IOException("Das Bildformat wird nicht unterstützt: " + datei.getFileName());
        }
        return bild;
    }

    public byte[] rohdaten(String id) throws IOException {
        Path datei = pfad(id).orElseThrow(() -> new IllegalArgumentException("Unbekanntes Foto: " + id));
        return Files.readAllBytes(datei);
    }

    public boolean loeschen(String id) throws IOException {
        Optional<Path> datei = pfad(id);
        if (datei.isEmpty()) {
            return false;
        }
        Files.delete(datei.get());
        return true;
    }

    public Optional<GeoPoint> aufnahmeort(String id) throws IOException {
        return pfad(id).flatMap(exifService::leseAufnahmeort);
    }

    private Optional<FotoInfo> lese(Path datei) {
        String dateiname = datei.getFileName().toString();
        int punkt = dateiname.lastIndexOf('.');
        if (punkt < 0) {
            return Optional.empty();
        }
        String id = dateiname.substring(0, punkt);
        try (ImageInputStream stream = ImageIO.createImageInputStream(datei.toFile())) {
            Iterator<ImageReader> reader = ImageIO.getImageReaders(stream);
            if (!reader.hasNext()) {
                return Optional.empty();
            }
            ImageReader bildLeser = reader.next();
            try {
                // Nur den Dateikopf lesen — das ganze Bild zu dekodieren wäre hier Verschwendung.
                bildLeser.setInput(stream);
                int breite = bildLeser.getWidth(0);
                int hoehe = bildLeser.getHeight(0);
                Optional<GeoPoint> ort = exifService.leseAufnahmeort(datei);
                return Optional.of(new FotoInfo(id, dateiname, breite, hoehe,
                        ort.map(GeoPoint::latitude).orElse(null),
                        ort.map(GeoPoint::longitude).orElse(null)));
            } finally {
                bildLeser.dispose();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String endungVon(String dateiname) {
        if (dateiname == null) {
            return "";
        }
        int punkt = dateiname.lastIndexOf('.');
        return punkt < 0 ? "" : dateiname.substring(punkt + 1).toLowerCase(Locale.ROOT);
    }
}
