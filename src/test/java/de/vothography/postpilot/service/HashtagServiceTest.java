package de.vothography.postpilot.service;

import de.vothography.postpilot.model.Hashtagbank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashtagServiceTest {

    private final HashtagService service = new HashtagService();
    private Hashtagbank bank;

    @BeforeEach
    void ladeBank(@TempDir Path tmp) throws IOException {
        HashtagStore store = new HashtagStore(tmp.resolve("hashtags.json"));
        store.load();
        bank = store.bank();
    }

    @Test
    @DisplayName("Die mitgelieferte Bank enthält alle Kategorien")
    void bankIstVollstaendig() {
        assertTrue(bank.gruppe("core").isPresent());
        assertTrue(bank.gruppe("siegerland").isPresent());
        assertTrue(bank.gruppe("sauerland").isPresent());
        assertTrue(bank.gruppe("nightscape").isPresent());
        assertTrue(bank.gruppe("reichweite").isPresent());
        assertFalse(bank.accounts().isEmpty());
    }

    @Test
    @DisplayName("Mit Region und Genre liegt der Vorschlag im Zielbereich 12 bis 18")
    void vorschlagLiegtImZielbereich() {
        var vorschlag = service.erstelle(bank,
                new HashtagService.Wunsch("sauerland", List.of("nightscape"), false, true));

        assertTrue(vorschlag.anzahl() >= HashtagService.ZIEL_MIN,
                "zu wenige Tags: " + vorschlag.anzahl());
        assertTrue(vorschlag.anzahl() <= HashtagService.ZIEL_MAX,
                "zu viele Tags: " + vorschlag.anzahl());
    }

    @Test
    @DisplayName("Ohne lokalen Bezug kommen keine regionalen Tags mit")
    void ohneRegionKeineRegionalenTags() {
        var vorschlag = service.erstelle(bank,
                new HashtagService.Wunsch(null, List.of("wald"), false, true));

        assertFalse(vorschlag.alleTags().contains("#siegerland"));
        assertFalse(vorschlag.alleTags().contains("#sauerland"));
        assertTrue(vorschlag.hinweis().contains("Region"), "Hinweis sollte zur Region raten");
    }

    @Test
    @DisplayName("Sauerland-Motive bekommen keine Siegerland-Tags und umgekehrt")
    void regionenWerdenNichtGemischt() {
        var sauerland = service.erstelle(bank,
                new HashtagService.Wunsch("sauerland", List.of("nightscape"), false, false));
        assertTrue(sauerland.alleTags().contains("#sauerland"));
        assertFalse(sauerland.alleTags().contains("#siegerland"));

        var siegerland = service.erstelle(bank,
                new HashtagService.Wunsch("siegerland", List.of("wald"), false, false));
        assertTrue(siegerland.alleTags().contains("#suedwestfalen_echt"));
        assertFalse(siegerland.alleTags().contains("#sauerland"));
    }

    @Test
    @DisplayName("Core-Tags sind immer dabei, aber höchstens drei")
    void coreTagsHoechstensDrei() {
        var vorschlag = service.erstelle(bank,
                new HashtagService.Wunsch("siegerland", List.of("nightscape"), true, true));

        var coreBlock = vorschlag.bloecke().stream()
                .filter(b -> b.gruppeId().equals("core"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, coreBlock.tags().size());
        assertTrue(coreBlock.tags().contains("#vothography"));
    }

    @Test
    @DisplayName("Auch mit allem Zubehör bleibt die Obergrenze von 18 stehen")
    void obergrenzeWirdEingehalten() {
        var vorschlag = service.erstelle(bank, new HashtagService.Wunsch(
                "siegerland", List.of("nightscape", "wald", "macro", "stimmung"), true, true));

        assertTrue(vorschlag.anzahl() <= HashtagService.ZIEL_MAX,
                "Obergrenze verletzt: " + vorschlag.anzahl());
        assertEquals(vorschlag.anzahl(), vorschlag.alleTags().stream().distinct().count(),
                "Tags dürfen sich nicht doppeln");
    }

    @Test
    @DisplayName("Presse-Tags kommen nur bei lokalem Bezug dazu")
    void presseNurMitRegion() {
        var ohneRegion = service.erstelle(bank,
                new HashtagService.Wunsch(null, List.of("nightscape"), true, false));
        assertFalse(ohneRegion.bloecke().stream().anyMatch(b -> b.gruppeId().equals("presse")));

        var mitRegion = service.erstelle(bank,
                new HashtagService.Wunsch("siegerland", List.of("nightscape"), true, false));
        assertTrue(mitRegion.bloecke().stream().anyMatch(b -> b.gruppeId().equals("presse")));
    }

    @Test
    @DisplayName("Mehrere Genres teilen sich das Budget, statt dass eines alles bekommt")
    void genresTeilenSichDasBudget() {
        var vorschlag = service.erstelle(bank,
                new HashtagService.Wunsch("siegerland", List.of("nightscape", "wald"), false, false));

        var nightscape = vorschlag.bloecke().stream()
                .filter(b -> b.gruppeId().equals("nightscape")).findFirst().orElseThrow();
        var wald = vorschlag.bloecke().stream()
                .filter(b -> b.gruppeId().equals("wald")).findFirst().orElseThrow();
        assertTrue(nightscape.tags().size() >= 2);
        assertTrue(wald.tags().size() >= 2);
    }

    @Test
    @DisplayName("Zur Region passende Accounts werden vorgeschlagen")
    void accountsPassenZurRegion() {
        var vorschlag = service.erstelle(bank,
                new HashtagService.Wunsch("sauerland", List.of(), false, false));

        assertTrue(vorschlag.accounts().contains("@discover_sauerland"));
        assertFalse(vorschlag.accounts().contains("@siwierleben"));
        assertTrue(vorschlag.accounts().contains("@sonyalpha"), "Equipment-Accounts passen immer");
    }
}
