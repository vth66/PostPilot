package de.vothography.postpilot.web;

import de.vothography.postpilot.model.Hashtagbank;
import de.vothography.postpilot.service.HashtagService;
import de.vothography.postpilot.service.HashtagStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Die kuratierte Hashtag-Sammlung und der daraus abgeleitete Vorschlag. */
@RestController
@RequestMapping("/api/hashtags")
public class HashtagController {

    private final HashtagStore store;
    private final HashtagService service;

    public HashtagController(HashtagStore store, HashtagService service) {
        this.store = store;
        this.service = service;
    }

    public record NeuerTag(String tag) {
    }

    @GetMapping
    public Hashtagbank bank() {
        return store.bank();
    }

    /**
     * @param region  Gegend mit echtem Bezug, leer lassen wenn es keinen gibt
     * @param genres  ids der Genre-Gruppen, kommagetrennt über wiederholten Parameter
     */
    @GetMapping("/vorschlag")
    public HashtagService.Vorschlag vorschlag(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(defaultValue = "false") boolean presse,
            @RequestParam(defaultValue = "true") boolean reichweite) {
        return service.erstelle(store.bank(),
                new HashtagService.Wunsch(region, genres == null ? List.of() : genres, presse, reichweite));
    }

    @PostMapping("/gruppen/{gruppeId}/tags")
    public Hashtagbank ergaenzen(@PathVariable String gruppeId, @RequestBody NeuerTag neuerTag)
            throws IOException {
        return store.ergaenzeTag(gruppeId, neuerTag.tag());
    }

    @DeleteMapping("/gruppen/{gruppeId}/tags")
    public Hashtagbank entfernen(@PathVariable String gruppeId, @RequestParam String tag)
            throws IOException {
        return store.entferneTag(gruppeId, tag);
    }
}
