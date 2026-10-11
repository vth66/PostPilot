package de.vothography.postpilot.config;

import de.vothography.postpilot.service.AppPaths;
import de.vothography.postpilot.service.ArchivService;
import de.vothography.postpilot.service.BildService;
import de.vothography.postpilot.service.CropService;
import de.vothography.postpilot.service.EntwurfStore;
import de.vothography.postpilot.service.ExifService;
import de.vothography.postpilot.service.FotoStore;
import de.vothography.postpilot.service.HashtagService;
import de.vothography.postpilot.service.HashtagStore;
import de.vothography.postpilot.service.PanoSplitService;
import de.vothography.postpilot.service.StammortStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Hier werden die Bausteine zusammengesteckt. Die Fachlogik selbst kennt kein Spring —
 * sie lässt sich dadurch ohne laufenden Server testen.
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public AppPaths appPaths(@Value("${postpilot.data-dir:}") String dataDir) throws IOException {
        AppPaths paths = new AppPaths(dataDir);
        paths.ensureDirectories();
        log.info("Datenverzeichnis: {}", paths.dataDir().toAbsolutePath());
        return paths;
    }

    @Bean
    public ExifService exifService() {
        return new ExifService();
    }

    @Bean
    public CropService cropService() {
        return new CropService();
    }

    @Bean
    public PanoSplitService panoSplitService() {
        return new PanoSplitService();
    }

    @Bean
    public StammortStore stammortStore(AppPaths paths) throws IOException {
        StammortStore store = new StammortStore(paths.stammorteFile());
        store.load();
        log.info("{} Stammorte geladen aus {}", store.alle().size(), store.file().toAbsolutePath());
        return store;
    }

    @Bean
    public HashtagStore hashtagStore(AppPaths paths) throws IOException {
        HashtagStore store = new HashtagStore(paths.dataDir().resolve("hashtags.json"));
        store.load();
        log.info("Hashtag-Bank geladen: {} Gruppen aus {}",
                store.bank().gruppen().size(), store.file().toAbsolutePath());
        return store;
    }

    @Bean
    public HashtagService hashtagService() {
        return new HashtagService();
    }

    @Bean
    public FotoStore fotoStore(AppPaths paths, ExifService exifService) throws IOException {
        return new FotoStore(paths, exifService);
    }

    @Bean
    public EntwurfStore entwurfStore(AppPaths paths) {
        return new EntwurfStore(paths);
    }

    @Bean
    public ArchivService archivService(AppPaths paths, FotoStore fotoStore,
                                       BildService bildService, EntwurfStore entwurfStore) {
        return new ArchivService(paths, fotoStore, bildService, entwurfStore);
    }

    @Bean
    public BildService bildService(FotoStore fotoStore, CropService cropService,
                                   PanoSplitService panoSplitService, AppPaths paths) {
        return new BildService(fotoStore, cropService, panoSplitService, paths);
    }
}
