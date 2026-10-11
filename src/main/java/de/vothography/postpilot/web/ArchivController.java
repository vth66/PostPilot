package de.vothography.postpilot.web;

import de.vothography.postpilot.service.ArchivService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Abgeschlossene Posts: ablegen, ansehen, wieder löschen. */
@RestController
@RequestMapping("/api/archiv")
public class ArchivController {

    private final ArchivService archivService;

    public ArchivController(ArchivService archivService) {
        this.archivService = archivService;
    }

    @GetMapping
    public List<ArchivService.ArchivEintrag> liste() throws IOException {
        return archivService.alle();
    }

    /** Legt den aktuellen Post ins Archiv und leert danach den Arbeitsbereich. */
    @PostMapping
    public ArchivService.ArchivEintrag archiviere(
            @RequestBody ArchivService.ArchivWunsch wunsch) throws IOException {
        return archivService.archiviere(wunsch);
    }

    @GetMapping(value = "/{id}/bild/{datei}", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] bild(@PathVariable String id, @PathVariable String datei) throws IOException {
        return archivService.bild(id, datei);
    }

    /** Nur die Originale weg — fertige Bilder, Caption und Hashtags bleiben. */
    @DeleteMapping("/{id}/originale")
    public ArchivService.ArchivEintrag loescheOriginale(@PathVariable String id) throws IOException {
        return archivService.loescheOriginale(id);
    }

    @DeleteMapping("/{id}")
    public void loesche(@PathVariable String id) throws IOException {
        archivService.loesche(id);
    }
}
