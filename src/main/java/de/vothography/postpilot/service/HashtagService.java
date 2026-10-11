package de.vothography.postpilot.service;

import de.vothography.postpilot.model.Accountgruppe;
import de.vothography.postpilot.model.Hashtagbank;
import de.vothography.postpilot.model.Hashtaggruppe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Stellt aus der Hashtag-Bank einen Vorschlag zusammen — nach der Regel aus dem bestehenden
 * Workflow: 2–3 Core-Tags, 5–8 regionale Tags bei echtem lokalem Bezug, 3–5 Genre-Tags passend
 * zum Motiv und höchstens 2–3 generische Reichweiten-Tags. Zielgröße insgesamt 12–18.
 *
 * <p>Der Vorschlag ist ausdrücklich ein Vorschlag: Die Oberfläche zeigt ihn zum Abwählen, weil
 * laut eigener Notiz in der Bank jeder Tag motivspezifisch geprüft werden soll statt kopiert.
 */
public final class HashtagService {

    public static final int ZIEL_MIN = 12;
    public static final int ZIEL_MAX = 18;

    private static final int BUDGET_CORE = 3;
    private static final int BUDGET_REGIONAL = 6;
    private static final int BUDGET_PRESSE = 2;
    private static final int BUDGET_GENRE = 5;
    private static final int BUDGET_REICHWEITE = 2;

    /**
     * @param region      Gegend mit echtem Bezug ("siegerland", "nrw", "sauerland") oder leer
     * @param genres      ids der Genre-Gruppen, die zum Motiv passen
     * @param presse      Presse-/Magazin-Tags mitnehmen (nur bei starken Motiven)
     * @param reichweite  generische Reichweiten-Tags mitnehmen
     */
    public record Wunsch(String region, List<String> genres, boolean presse, boolean reichweite) {

        public Wunsch {
            genres = genres == null ? List.of() : List.copyOf(genres);
        }
    }

    /** Ein Block des Vorschlags, nach Gruppen getrennt, damit die Oberfläche ihn erklären kann. */
    public record Block(String gruppeId, String gruppeName, List<String> tags) {
    }

    public record Vorschlag(List<Block> bloecke, List<String> alleTags, List<String> accounts,
                            int anzahl, String hinweis) {
    }

    public Vorschlag erstelle(Hashtagbank bank, Wunsch wunsch) {
        List<Block> bloecke = new ArrayList<>();
        Set<String> gesammelt = new LinkedHashSet<>();

        bank.vomTyp(Hashtaggruppe.Typ.CORE).stream().findFirst()
                .ifPresent(g -> ergaenze(bloecke, gesammelt, g, BUDGET_CORE));

        Optional<Hashtaggruppe> regional = bank.regionalGruppe(wunsch.region());
        regional.ifPresent(g -> ergaenze(bloecke, gesammelt, g, BUDGET_REGIONAL));

        if (wunsch.presse() && regional.isPresent()) {
            bank.vomTyp(Hashtaggruppe.Typ.PRESSE).stream().findFirst()
                    .ifPresent(g -> ergaenze(bloecke, gesammelt, g, BUDGET_PRESSE));
        }

        ergaenzeGenres(bank, wunsch, bloecke, gesammelt);

        if (wunsch.reichweite()) {
            bank.vomTyp(Hashtaggruppe.Typ.REICHWEITE).stream().findFirst()
                    .ifPresent(g -> ergaenze(bloecke, gesammelt, g, BUDGET_REICHWEITE));
        }

        List<Block> begrenzt = aufObergrenzeKuerzen(bloecke);
        List<String> alleTags = begrenzt.stream().flatMap(b -> b.tags().stream()).toList();

        return new Vorschlag(begrenzt, alleTags, accountsFuer(bank, wunsch.region()),
                alleTags.size(), hinweisZu(alleTags.size(), regional.isPresent(), wunsch));
    }

    /** Genre-Tags werden reihum verteilt, damit bei mehreren Genres keines leer ausgeht. */
    private void ergaenzeGenres(Hashtagbank bank, Wunsch wunsch, List<Block> bloecke, Set<String> gesammelt) {
        List<Hashtaggruppe> gewaehlt = wunsch.genres().stream()
                .map(bank::gruppe)
                .flatMap(Optional::stream)
                .filter(g -> g.typ() == Hashtaggruppe.Typ.GENRE)
                .toList();
        if (gewaehlt.isEmpty()) {
            return;
        }

        List<List<String>> ausgewaehlt = new ArrayList<>();
        for (int i = 0; i < gewaehlt.size(); i++) {
            ausgewaehlt.add(new ArrayList<>());
        }
        int vergeben = 0;
        int runde = 0;
        while (vergeben < BUDGET_GENRE) {
            boolean etwasGenommen = false;
            for (int i = 0; i < gewaehlt.size() && vergeben < BUDGET_GENRE; i++) {
                List<String> tags = gewaehlt.get(i).tags();
                if (runde < tags.size() && gesammelt.add(tags.get(runde))) {
                    ausgewaehlt.get(i).add(tags.get(runde));
                    vergeben++;
                    etwasGenommen = true;
                }
            }
            if (!etwasGenommen) {
                break;
            }
            runde++;
        }

        for (int i = 0; i < gewaehlt.size(); i++) {
            if (!ausgewaehlt.get(i).isEmpty()) {
                bloecke.add(new Block(gewaehlt.get(i).id(), gewaehlt.get(i).name(), List.copyOf(ausgewaehlt.get(i))));
            }
        }
    }

    private void ergaenze(List<Block> bloecke, Set<String> gesammelt, Hashtaggruppe gruppe, int budget) {
        List<String> genommen = new ArrayList<>();
        for (String tag : gruppe.tags()) {
            if (genommen.size() >= budget) {
                break;
            }
            if (gesammelt.add(tag)) {
                genommen.add(tag);
            }
        }
        if (!genommen.isEmpty()) {
            bloecke.add(new Block(gruppe.id(), gruppe.name(), List.copyOf(genommen)));
        }
    }

    /** Über 18 Tags wird von hinten gekürzt — die generischen Tags fliegen also zuerst raus. */
    private List<Block> aufObergrenzeKuerzen(List<Block> bloecke) {
        int gesamt = bloecke.stream().mapToInt(b -> b.tags().size()).sum();
        if (gesamt <= ZIEL_MAX) {
            return List.copyOf(bloecke);
        }
        int zuViel = gesamt - ZIEL_MAX;
        List<Block> ergebnis = new ArrayList<>(bloecke);
        for (int i = ergebnis.size() - 1; i >= 0 && zuViel > 0; i--) {
            Block block = ergebnis.get(i);
            int behalten = Math.max(0, block.tags().size() - zuViel);
            zuViel -= block.tags().size() - behalten;
            if (behalten == 0) {
                ergebnis.remove(i);
            } else {
                ergebnis.set(i, new Block(block.gruppeId(), block.gruppeName(),
                        List.copyOf(block.tags().subList(0, behalten))));
            }
        }
        return List.copyOf(ergebnis);
    }

    private List<String> accountsFuer(Hashtagbank bank, String region) {
        List<String> handles = new ArrayList<>();
        for (Accountgruppe gruppe : bank.accounts()) {
            if (gruppe.region() == null || gruppe.region().equals(region)) {
                handles.addAll(gruppe.handles().stream().limit(3).toList());
            }
        }
        return List.copyOf(handles);
    }

    private String hinweisZu(int anzahl, boolean mitRegion, Wunsch wunsch) {
        if (anzahl < ZIEL_MIN) {
            int fehlen = ZIEL_MIN - anzahl;
            if (!mitRegion) {
                return "Noch " + fehlen + " Tags bis zur Zielgröße. Eine Region bringt die meisten dazu "
                        + "— aber nur wählen, wenn das Motiv echten lokalen Bezug hat. Sonst lieber bei "
                        + anzahl + " Tags bleiben als auf Verdacht auffüllen.";
            }
            if (wunsch.genres().isEmpty()) {
                return "Noch " + fehlen + " Tags bis zur Zielgröße — ein Genre passend zum Motiv wählen.";
            }
            return "Noch " + fehlen + " Tags bis zur Zielgröße von " + ZIEL_MIN + ".";
        }
        return "Im Zielbereich " + ZIEL_MIN + "–" + ZIEL_MAX + ". Jeden Tag am Motiv prüfen — "
                + "im Zweifel weglassen statt auf Verdacht mitnehmen.";
    }
}
