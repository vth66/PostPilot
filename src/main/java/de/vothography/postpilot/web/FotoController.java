package de.vothography.postpilot.web;

import de.vothography.postpilot.model.CropFormat;
import de.vothography.postpilot.service.BildService;
import de.vothography.postpilot.service.FotoStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Alles rund um die Fotos: hochladen, anzeigen, zuschneiden, exportieren. */
@RestController
@RequestMapping("/api/fotos")
public class FotoController {

    private final FotoStore fotoStore;
    private final BildService bildService;
    private final de.vothography.postpilot.service.EntwurfStore entwurfStore;

    public FotoController(FotoStore fotoStore, BildService bildService,
                          de.vothography.postpilot.service.EntwurfStore entwurfStore) {
        this.fotoStore = fotoStore;
        this.bildService = bildService;
        this.entwurfStore = entwurfStore;
    }

    @GetMapping
    public List<FotoStore.FotoInfo> liste() throws IOException {
        return fotoStore.alle();
    }

    @PostMapping
    public FotoStore.FotoInfo hochladen(@RequestParam("datei") MultipartFile datei) throws IOException {
        try (InputStream daten = datei.getInputStream()) {
            return fotoStore.speichern(daten, datei.getOriginalFilename());
        }
    }

    /** Legt eine zweite Fassung desselben Fotos an — direkt hinter dem Original. */
    @PostMapping("/{id}/duplizieren")
    public FotoStore.FotoInfo duplizieren(@PathVariable String id) throws IOException {
        return fotoStore.duplizieren(id);
    }

    /** Das ganze Foto im Postformat, mit Freiraum oben und unten statt Beschnitt. */
    @GetMapping(value = "/{id}/ganz", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] ganzesBild(@PathVariable String id,
                             @RequestParam(defaultValue = "0.8") double seitenverhaeltnis,
                             @RequestParam(defaultValue = "schwarz") String fuellung)
            throws IOException {
        return bildService.ganzesBild(id, seitenverhaeltnis, fuellung);
    }

    public record Reihenfolge(List<String> ids) {
    }

    /** Legt fest, in welcher Folge die Fotos im Carousel erscheinen. */
    @PutMapping("/reihenfolge")
    public List<FotoStore.FotoInfo> reihenfolge(@RequestBody Reihenfolge reihenfolge) throws IOException {
        fotoStore.setzeReihenfolge(reihenfolge.ids());
        return fotoStore.alle();
    }

    @PostMapping("/carousel/export")
    public BildService.ExportErgebnis carouselExportieren(
            @RequestBody BildService.CarouselWunsch wunsch) throws IOException {
        return bildService.exportiereCarousel(wunsch);
    }

    /**
     * Dasselbe Carousel als ZIP zum Herunterladen. Läuft die Anwendung auf der NAS, liegt
     * der Export-Ordner dort — gebraucht werden die Dateien aber auf dem Rechner, von dem
     * aus Metricool bedient wird.
     */
    @PostMapping(value = "/carousel/zip", produces = "application/zip")
    public ResponseEntity<byte[]> carouselAlsZip(
            @RequestBody BildService.CarouselWunsch wunsch) throws IOException {
        return alsDownload(bildService.carouselAlsZip(wunsch), bildService.zipName());
    }

    @PostMapping(value = "/{id}/zip", produces = "application/zip")
    public ResponseEntity<byte[]> einzelnAlsZip(@PathVariable String id,
                                                @RequestParam(defaultValue = "FEED_4_5") CropFormat format,
                                                @RequestParam(defaultValue = "0.5") double fokusX,
                                                @RequestParam(defaultValue = "0.5") double fokusY)
            throws IOException {
        return alsDownload(bildService.alsZip(id, format, fokusX, fokusY), bildService.zipName());
    }

    private static ResponseEntity<byte[]> alsDownload(byte[] daten, String dateiname) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + dateiname + "\"")
                .header("Content-Type", "application/zip")
                .body(daten);
    }

    @DeleteMapping("/{id}")
    public void loeschen(@PathVariable String id) throws IOException {
        fotoStore.loeschen(id);
    }

    /** Alle Fotos verwerfen — der Anfang eines neuen Posts. */
    @DeleteMapping
    public void alleLoeschen() throws IOException {
        for (FotoStore.FotoInfo foto : fotoStore.alle()) {
            fotoStore.loeschen(foto.id());
        }
        fotoStore.setzeReihenfolge(List.of());
        entwurfStore.loesche();
    }

    /** Das unveränderte Originalfoto, für die Vorschau in der Zuschnittmaske. */
    @GetMapping(value = "/{id}/original", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] original(@PathVariable String id) throws IOException {
        return fotoStore.rohdaten(id);
    }

    /** Der fertige Zuschnitt im gewählten Format — genau so, wie er später hochgeladen wird. */
    @GetMapping(value = "/{id}/crop", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] crop(@PathVariable String id,
                       @RequestParam(defaultValue = "FEED_4_5") CropFormat format,
                       @RequestParam(required = false) Double seitenverhaeltnis,
                       @RequestParam(defaultValue = "0.5") double fokusX,
                       @RequestParam(defaultValue = "0.5") double fokusY) throws IOException {
        // Mit seitenverhaeltnis schneidet der Server auf ein freies Format zu — das braucht
        // die Carousel-Vorschau, in der alle Bilder dem ersten folgen müssen.
        return seitenverhaeltnis == null
                ? bildService.crop(id, format, fokusX, fokusY)
                : bildService.crop(id, seitenverhaeltnis, fokusX, fokusY);
    }

    /** Wie viele Ausschnitte möglich sind und wo genau die Kanten liegen. */
    @GetMapping("/{id}/pano")
    public BildService.PanoUebersicht pano(@PathVariable String id,
                                           @RequestParam(required = false) Integer anzahl,
                                           @RequestParam(defaultValue = "0.8") double zielAspect,
                                           @RequestParam(defaultValue = "0.5") double fokusY)
            throws IOException {
        return bildService.panoUebersicht(id, anzahl, zielAspect, fokusY);
    }

    @GetMapping(value = "/{id}/pano/{index}", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] panoSlice(@PathVariable String id,
                            @PathVariable int index,
                            @RequestParam int anzahl,
                            @RequestParam(defaultValue = "0.8") double zielAspect,
                            @RequestParam(defaultValue = "0.5") double fokusY) throws IOException {
        return bildService.panoSlice(id, anzahl, index, zielAspect, fokusY);
    }

    @PostMapping("/{id}/export")
    public BildService.ExportErgebnis exportieren(@PathVariable String id,
                                                  @RequestParam(defaultValue = "FEED_4_5") CropFormat format,
                                                  @RequestParam(defaultValue = "0.5") double fokusX,
                                                  @RequestParam(defaultValue = "0.5") double fokusY)
            throws IOException {
        return bildService.exportiere(id, format, fokusX, fokusY);
    }
}
