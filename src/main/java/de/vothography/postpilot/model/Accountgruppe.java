package de.vothography.postpilot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Accounts, die bei passendem Motiv markiert werden können.
 *
 * @param region auf welche Gegend sich die Gruppe bezieht; {@code null} = immer passend
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Accountgruppe(String id, String name, String region, List<String> handles) {

    public Accountgruppe {
        handles = handles == null ? List.of() : List.copyOf(handles);
    }
}
