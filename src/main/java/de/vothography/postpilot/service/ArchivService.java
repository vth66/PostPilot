package de.vothography.postpilot.service;

import de.vothography.postpilot.model.ArchivPost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Legt abgeschlossene Posts unter {@code archiv/} ab und räumt den Arbeitsbereich dabei leer.
 *
 * <p>Jeder Post bekommt einen eigenen Ordner — anders ginge „behalten" nicht, denn behaltene
 * Fotos würden sich sonst unter den nächsten Post mischen. Die Originale kommen nur mit, wenn
 * das ausdrücklich gewünscht ist, und lassen sich später einzeln wieder löschen, ohne den
 * Post selbst zu verlieren.
 */
public final class ArchivService {

    /** Ordnernamen erzeugt diese Klasse selbst — alles andere wird nicht angefasst. */
    private static final Pattern ERLAUBTE_ID = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_[a-z0-9-]{1,60}");

    private static final String UNTERORDNER_ORIGINALE = "originale";
    private static final String BESCHREIBUNG = "post.json";

    private final AppPaths paths;
    private final FotoStore fotoStore;
    private final BildService bildService;
    private final EntwurfStore entwurfStore;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ArchivService(AppPaths paths, FotoStore fotoStore, BildService bildService,
                         EntwurfStore entwurfStore) {
        this.paths = paths;
        this.fotoStore = fotoStore;
        this.bildService = bildService;
        this.entwurfStore = entwurfStore;
    }

    /** Was beim Abschließen eines Posts mitgegeben wird. */
    public record ArchivWunsch(String name, String caption, List<String> hashtags, String ort,
                               String format, boolean mitOriginalen,
                               BildService.CarouselWunsch post) {
    }

    /** Ergebnis: der angelegte Post und wie viel Platz er belegt. */
    public record ArchivEintrag(ArchivPost post, long bytes, long originalBytes) {
    }

    public Path archivDir() {
        return paths.dataDir().resolve("archiv");
    }

    /**
     * Schreibt den Post ins Archiv und leert danach den Arbeitsbereich. Erst wenn alles im
     * Archiv liegt, werden die Arbeitsfotos gelöscht — bricht etwas ab, ist nichts verloren.
     */
    public ArchivEintrag archiviere(ArchivWunsch wunsch) throws IOException {
        if (wunsch.post() == null || wunsch.post().bilder().isEmpty()) {
            throw new IllegalArgumentException("Der Post enthält kein Bild");
        }
        Path ziel = Files.createDirectories(freierOrdner(wunsch.name()));

        List<BildService.FertigesBild> fertige = bildService.carouselBilder(wunsch.post());
        List<ArchivPost.Bild> bilder = new ArrayList<>(fertige.size());
        for (int i = 0; i < fertige.size(); i++) {
            BildService.FertigesBild fertig = fertige.get(i);
            BildService.CarouselBild quelle = wunsch.post().bilder().get(i);
            Files.write(ziel.resolve(fertig.name()), fertig.daten());
            bilder.add(new ArchivPost.Bild(fertig.name(), dateinameVon(quelle.id()),
                    artVon(quelle), quelle.sliceIndex(), quelle.panoAnzahl()));
        }

        List<String> originale = wunsch.mitOriginalen()
                ? kopiereOriginale(ziel)
                : List.of();

        ArchivPost post = new ArchivPost(ziel.getFileName().toString(),
                wunsch.name() == null || wunsch.name().isBlank() ? "Ohne Titel" : wunsch.name().trim(),
                LocalDateTime.now().toString(),
                wunsch.format(), wunsch.post().seitenverhaeltnis(),
                wunsch.caption(), wunsch.hashtags(), wunsch.ort(), bilder, originale);
        schreibeBeschreibung(ziel, post);
        // Derselbe Begleitzettel wie im Export. Wer einen alten Post noch einmal braucht,
        // soll Caption und Hashtags kopieren können, ohne in der JSON-Datei zu suchen.
        Files.writeString(ziel.resolve("post.txt"),
                BildService.begleitzettel(wunsch.post(), fertige.size()), StandardCharsets.UTF_8);

        // Jetzt ist alles gesichert — der Arbeitsbereich darf leer werden.
        for (FotoStore.FotoInfo foto : fotoStore.alle()) {
            fotoStore.loeschen(foto.id());
        }
        fotoStore.setzeReihenfolge(List.of());
        entwurfStore.loesche();

        return new ArchivEintrag(post, groesse(ziel), groesse(ziel.resolve(UNTERORDNER_ORIGINALE)));
    }

    /** Alle Posts im Archiv, der jüngste zuerst. */
    public List<ArchivEintrag> alle() throws IOException {
        Path basis = archivDir();
        if (Files.notExists(basis)) {
            return List.of();
        }
        List<ArchivEintrag> eintraege = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(basis)) {
            for (Path ordner : stream) {
                if (!Files.isDirectory(ordner)) {
                    continue;
                }
                lese(ordner).ifPresent(post -> eintraege.add(new ArchivEintrag(post,
                        groesse(ordner), groesse(ordner.resolve(UNTERORDNER_ORIGINALE)))));
            }
        }
        eintraege.sort(Comparator.comparing((ArchivEintrag e) -> e.post().id()).reversed());
        return eintraege;
    }

    /** Ein fertiges Bild aus dem Archiv, für die Anzeige in der Liste. */
    public byte[] bild(String id, String datei) throws IOException {
        Path ordner = pruefeOrdner(id);
        if (!datei.matches("\\d{2}\\.jpg")) {
            throw new IllegalArgumentException("Unbekannte Bilddatei: " + datei);
        }
        Path pfad = ordner.resolve(datei);
        if (Files.notExists(pfad)) {
            throw new IllegalArgumentException("Dieses Bild gibt es im Archiv nicht: " + datei);
        }
        return Files.readAllBytes(pfad);
    }

    /**
     * Löscht nur die Originaldateien eines archivierten Posts. Die fertigen Bilder, Caption
     * und Hashtags bleiben — das ist der Fall „Platz schaffen, Erinnerung behalten".
     */
    public ArchivEintrag loescheOriginale(String id) throws IOException {
        Path ordner = pruefeOrdner(id);
        Path originale = ordner.resolve(UNTERORDNER_ORIGINALE);
        loescheBaum(originale);

        ArchivPost alt = lese(ordner).orElseThrow(
                () -> new IllegalArgumentException("Kein Post in " + id));
        ArchivPost neu = new ArchivPost(alt.id(), alt.name(), alt.erstellt(), alt.format(),
                alt.seitenverhaeltnis(), alt.caption(), alt.hashtags(), alt.ort(),
                alt.bilder(), List.of());
        schreibeBeschreibung(ordner, neu);
        return new ArchivEintrag(neu, groesse(ordner), 0);
    }

    /** Löscht den ganzen archivierten Post. */
    public void loesche(String id) throws IOException {
        loescheBaum(pruefeOrdner(id));
    }

    /* ------------------------------------------------------------------ intern */

    private List<String> kopiereOriginale(Path ziel) throws IOException {
        Path unterordner = Files.createDirectories(ziel.resolve(UNTERORDNER_ORIGINALE));
        List<String> namen = new ArrayList<>();
        for (FotoStore.FotoInfo foto : fotoStore.alle()) {
            Optional<Path> quelle = fotoStore.pfad(foto.id());
            if (quelle.isEmpty()) {
                continue;
            }
            String name = quelle.get().getFileName().toString();
            Files.copy(quelle.get(), unterordner.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            namen.add(name);
        }
        return namen;
    }

    private void schreibeBeschreibung(Path ordner, ArchivPost post) throws IOException {
        mapper.writeValue(ordner.resolve(BESCHREIBUNG).toFile(), post);
    }

    private Optional<ArchivPost> lese(Path ordner) {
        Path datei = ordner.resolve(BESCHREIBUNG);
        if (Files.notExists(datei)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(datei)) {
            return Optional.ofNullable(mapper.readValue(in, ArchivPost.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Prüft die Kennung streng und stellt sicher, dass der Ordner wirklich unter
     * {@code archiv/} liegt. Ohne diese Prüfung könnte ein zusammengesetzter Pfad aus dem
     * Browser irgendeinen Ordner auf der NAS löschen.
     */
    private Path pruefeOrdner(String id) throws IOException {
        if (id == null || !ERLAUBTE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Ungültige Archiv-Kennung: " + id);
        }
        Path basis = archivDir().toAbsolutePath().normalize();
        Path ordner = basis.resolve(id).normalize();
        if (!ordner.startsWith(basis) || !Files.isDirectory(ordner)) {
            throw new IllegalArgumentException("Diesen archivierten Post gibt es nicht: " + id);
        }
        return ordner;
    }

    private Path freierOrdner(String name) throws IOException {
        String basisName = LocalDate.now() + "_" + kurzname(name);
        Path basis = archivDir();
        Path ziel = basis.resolve(basisName);
        for (int nr = 2; Files.exists(ziel) && nr < 100; nr++) {
            ziel = basis.resolve(basisName + "-" + nr);
        }
        if (Files.exists(ziel)) {
            throw new IOException("Zu viele Posts mit demselben Namen am selben Tag");
        }
        return ziel;
    }

    /** Macht aus einem Titel einen Ordnernamen, der auf jedem Dateisystem funktioniert. */
    public static String kurzname(String name) {
        String roh = (name == null ? "" : name).toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (roh.isBlank()) {
            return "post";
        }
        return roh.length() > 40 ? roh.substring(0, 40).replaceAll("-+$", "") : roh;
    }

    private static String artVon(BildService.CarouselBild bild) {
        if (bild.panoAnzahl() != null) {
            return "pano";
        }
        return (bild.fuellung() != null && !bild.fuellung().isBlank()) ? "ganz" : "ausschnitt";
    }

    private String dateinameVon(String fotoId) {
        try {
            return fotoStore.pfad(fotoId).map(p -> p.getFileName().toString()).orElse(fotoId);
        } catch (IOException e) {
            return fotoId;
        }
    }

    private static long groesse(Path ordner) {
        if (Files.notExists(ordner)) {
            return 0;
        }
        long summe = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ordner)) {
            for (Path eintrag : stream) {
                summe += Files.isDirectory(eintrag) ? groesse(eintrag) : Files.size(eintrag);
            }
        } catch (IOException e) {
            return summe;
        }
        return summe;
    }

    private static void loescheBaum(Path wurzel) throws IOException {
        if (Files.notExists(wurzel)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wurzel)) {
            for (Path eintrag : stream) {
                if (Files.isDirectory(eintrag)) {
                    loescheBaum(eintrag);
                } else {
                    Files.delete(eintrag);
                }
            }
        }
        Files.delete(wurzel);
    }
}
