package de.vothography.postpilot.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Schreibt JPEGs mit fester Qualität. Die Standardeinstellung von ImageIO ist für Fotos
 * zu grob — bei 0,92 sieht man im Instagram-Feed keinen Unterschied zum Original.
 */
public final class JpegWriter {

    private static final float QUALITY = 0.92f;

    private JpegWriter() {
    }

    public static byte[] toBytes(BufferedImage bild) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        write(bild, buffer);
        return buffer.toByteArray();
    }

    public static void toFile(BufferedImage bild, Path ziel) throws IOException {
        try (OutputStream out = Files.newOutputStream(ziel)) {
            write(bild, out);
        }
    }

    private static void write(BufferedImage bild, OutputStream out) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("Keine JPEG-Unterstützung in dieser Java-Laufzeit gefunden");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
        }
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(bild, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
