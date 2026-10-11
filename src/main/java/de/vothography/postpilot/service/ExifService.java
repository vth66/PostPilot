package de.vothography.postpilot.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import de.vothography.postpilot.model.GeoPoint;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * Liest Aufnahmeort und -zeit aus den EXIF-Daten. Läuft komplett lokal —
 * die Ortsvorschläge brauchen deshalb keinen Dienst im Internet.
 */
public final class ExifService {

    public Optional<GeoPoint> leseAufnahmeort(Path bild) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(bild.toFile());
            for (GpsDirectory gps : metadata.getDirectoriesOfType(GpsDirectory.class)) {
                var location = gps.getGeoLocation();
                if (location != null && !location.isZero()) {
                    return Optional.of(new GeoPoint(location.getLatitude(), location.getLongitude()));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            // Ein Foto ohne verwertbare Metadaten ist kein Fehlerfall, nur kein Ortsvorschlag.
            return Optional.empty();
        }
    }

    public Optional<LocalDateTime> leseAufnahmezeit(Path bild) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(bild.toFile());
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exif == null) {
                return Optional.empty();
            }
            Date original = exif.getDateOriginal();
            if (original == null) {
                return Optional.empty();
            }
            return Optional.of(LocalDateTime.ofInstant(original.toInstant(), ZoneId.systemDefault()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
