package de.vothography.postpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.vothography.postpilot.model.Hashtagbank;
import de.vothography.postpilot.model.Hashtaggruppe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hält die Hashtag-Bank als JSON-Datei im Datenverzeichnis — bewusst von Hand editierbar,
 * genauso wie die Stammorte.
 */
public final class HashtagStore {

    private static final String DEFAULT_RESOURCE = "/de/vothography/postpilot/hashtags-default.json";

    private final Path file;
    private final ObjectMapper mapper;
    private Hashtagbank bank = Hashtagbank.leer();

    public HashtagStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Lädt die Bank; fehlt die Datei, wird sie aus der mitgelieferten Sammlung angelegt. */
    public void load() throws IOException {
        if (Files.notExists(file)) {
            bank = readDefaults();
            save();
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            bank = mapper.readValue(in, Hashtagbank.class);
        }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), bank);
    }

    public Hashtagbank bank() {
        return bank;
    }

    public Path file() {
        return file;
    }

    /** Fügt einen Tag hinten an eine Gruppe an; bereits vorhandene Tags bleiben unverändert. */
    public Hashtagbank ergaenzeTag(String gruppeId, String tag) throws IOException {
        String sauber = normalisiere(tag);
        List<Hashtaggruppe> gruppen = new ArrayList<>();
        boolean getroffen = false;
        for (Hashtaggruppe gruppe : bank.gruppen()) {
            if (gruppe.id().equals(gruppeId) && !gruppe.tags().contains(sauber)) {
                List<String> tags = new ArrayList<>(gruppe.tags());
                tags.add(sauber);
                gruppen.add(new Hashtaggruppe(gruppe.id(), gruppe.name(), gruppe.typ(), gruppe.region(), tags));
                getroffen = true;
            } else {
                gruppen.add(gruppe);
                getroffen |= gruppe.id().equals(gruppeId);
            }
        }
        if (!getroffen) {
            throw new IllegalArgumentException("Unbekannte Hashtag-Gruppe: " + gruppeId);
        }
        bank = new Hashtagbank(gruppen, bank.accounts());
        save();
        return bank;
    }

    public Hashtagbank entferneTag(String gruppeId, String tag) throws IOException {
        String sauber = normalisiere(tag);
        List<Hashtaggruppe> gruppen = bank.gruppen().stream()
                .map(gruppe -> gruppe.id().equals(gruppeId)
                        ? new Hashtaggruppe(gruppe.id(), gruppe.name(), gruppe.typ(), gruppe.region(),
                                gruppe.tags().stream().filter(t -> !t.equals(sauber)).toList())
                        : gruppe)
                .toList();
        bank = new Hashtagbank(gruppen, bank.accounts());
        save();
        return bank;
    }

    private static String normalisiere(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Leerer Hashtag");
        }
        String sauber = tag.trim().replaceAll("\\s+", "");
        return sauber.startsWith("#") ? sauber : "#" + sauber;
    }

    private Hashtagbank readDefaults() throws IOException {
        try (InputStream in = HashtagStore.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return Hashtagbank.leer();
            }
            return mapper.readValue(in, Hashtagbank.class);
        }
    }
}
