package de.vothography.postpilot.web;

import de.vothography.postpilot.service.EntwurfStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Der Arbeitsstand des gerade offenen Posts. Die Oberfläche legt ihn nach jeder Änderung ab,
 * damit ein Neuladen der Seite nichts mehr kostet.
 */
@RestController
@RequestMapping("/api/entwurf")
public class EntwurfController {

    private final EntwurfStore store;

    public EntwurfController(EntwurfStore store) {
        this.store = store;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public String lies() throws IOException {
        return store.lies();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public void schreibe(@RequestBody String inhalt) throws IOException {
        store.schreibe(inhalt);
    }

    @DeleteMapping
    public void loesche() throws IOException {
        store.loesche();
    }
}
