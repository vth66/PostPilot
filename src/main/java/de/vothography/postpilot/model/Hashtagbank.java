package de.vothography.postpilot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Optional;

/** Die gesamte kuratierte Sammlung: Hashtag-Gruppen und markierbare Accounts. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Hashtagbank(List<Hashtaggruppe> gruppen, List<Accountgruppe> accounts) {

    public Hashtagbank {
        gruppen = gruppen == null ? List.of() : List.copyOf(gruppen);
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    public static Hashtagbank leer() {
        return new Hashtagbank(List.of(), List.of());
    }

    public Optional<Hashtaggruppe> gruppe(String id) {
        return gruppen.stream().filter(g -> g.id().equals(id)).findFirst();
    }

    public List<Hashtaggruppe> vomTyp(Hashtaggruppe.Typ typ) {
        return gruppen.stream().filter(g -> g.typ() == typ).toList();
    }

    /** Die regionale Gruppe zu einer Gegend, etwa "sauerland". */
    public Optional<Hashtaggruppe> regionalGruppe(String region) {
        if (region == null || region.isBlank()) {
            return Optional.empty();
        }
        return gruppen.stream()
                .filter(g -> g.typ() == Hashtaggruppe.Typ.REGIONAL && region.equals(g.region()))
                .findFirst();
    }
}
