package de.vothography.postpilot.service;

import de.vothography.postpilot.model.CropFormat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Zuschnitt auf ein Zielformat. Der Ausschnitt ist immer der größtmögliche im Bild und
 * lässt sich über einen Fokuspunkt verschieben, statt stur mittig zu sitzen.
 */
public final class CropService {

    /**
     * @param focusX 0,0 = ganz links, 0,5 = mittig, 1,0 = ganz rechts
     * @param focusY 0,0 = ganz oben, 0,5 = mittig, 1,0 = ganz unten
     */
    public Rectangle computeCrop(int sourceWidth, int sourceHeight, double targetAspect, double focusX, double focusY) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Bildmaße müssen positiv sein");
        }
        if (targetAspect <= 0) {
            throw new IllegalArgumentException("Seitenverhältnis muss positiv sein");
        }

        double sourceAspect = (double) sourceWidth / sourceHeight;
        int cropWidth;
        int cropHeight;
        if (sourceAspect > targetAspect) {
            cropHeight = sourceHeight;
            cropWidth = (int) Math.round(sourceHeight * targetAspect);
        } else {
            cropWidth = sourceWidth;
            cropHeight = (int) Math.round(sourceWidth / targetAspect);
        }
        cropWidth = Math.min(cropWidth, sourceWidth);
        cropHeight = Math.min(cropHeight, sourceHeight);

        int x = clamp((int) Math.round(focusX * sourceWidth - cropWidth / 2.0), 0, sourceWidth - cropWidth);
        int y = clamp((int) Math.round(focusY * sourceHeight - cropHeight / 2.0), 0, sourceHeight - cropHeight);
        return new Rectangle(x, y, cropWidth, cropHeight);
    }

    public Rectangle computeCrop(int sourceWidth, int sourceHeight, CropFormat format) {
        return computeCrop(sourceWidth, sourceHeight, format.aspectRatio(), 0.5, 0.5);
    }

    public BufferedImage crop(BufferedImage source, Rectangle area) {
        BufferedImage target = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
        target.createGraphics().drawImage(
                source.getSubimage(area.x, area.y, area.width, area.height), 0, 0, null);
        return target;
    }

    /** Skaliert auf die Zielmaße des Formats — Instagram rechnet ohnehin auf 1080 px Breite herunter. */
    public BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        var graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return target;
    }

    /** Womit der Freiraum neben dem ganzen Bild gefüllt wird. */
    public enum Fuellung {
        SCHWARZ,
        WEISS,
        WEICH;

        public static Fuellung ausText(String text) {
            if (text == null || text.isBlank()) {
                return SCHWARZ;
            }
            return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "weiss", "weiß", "white" -> WEISS;
                case "weich", "blur" -> WEICH;
                default -> SCHWARZ;
            };
        }
    }

    /**
     * Setzt das <em>ganze</em> Bild in das Zielformat, statt etwas abzuschneiden — oben und
     * unten (bei Hochformaten links und rechts) bleibt Freiraum. Gedacht für ein Panorama
     * als Auftakt eines Carousels: Erst sieht man die ganze Szene, danach die Ausschnitte.
     */
    public BufferedImage einpassen(BufferedImage source, int targetWidth, int targetHeight,
                                   Fuellung fuellung) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Zielmaße müssen positiv sein");
        }
        BufferedImage ziel = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ziel.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        zeichneHintergrund(g, source, targetWidth, targetHeight, fuellung);

        // Das ganze Bild hineinpassen: der kleinere der beiden Maßstäbe entscheidet.
        double massstab = Math.min((double) targetWidth / source.getWidth(),
                (double) targetHeight / source.getHeight());
        int breite = Math.max(1, (int) Math.round(source.getWidth() * massstab));
        int hoehe = Math.max(1, (int) Math.round(source.getHeight() * massstab));
        g.drawImage(source, (targetWidth - breite) / 2, (targetHeight - hoehe) / 2, breite, hoehe, null);
        g.dispose();
        return ziel;
    }

    private void zeichneHintergrund(Graphics2D g, BufferedImage source,
                                    int targetWidth, int targetHeight, Fuellung fuellung) {
        if (fuellung == Fuellung.WEICH) {
            // Billiger, aber überzeugender Weichzeichner: stark verkleinern und bilinear
            // wieder aufziehen. Das Ergebnis ist eine weiche Farbfläche aus dem Foto selbst.
            BufferedImage klein = resize(source, 24, Math.max(1, (int) Math.round(
                    24.0 * source.getHeight() / source.getWidth())));
            double massstab = Math.max((double) targetWidth / klein.getWidth(),
                    (double) targetHeight / klein.getHeight());
            int breite = (int) Math.ceil(klein.getWidth() * massstab);
            int hoehe = (int) Math.ceil(klein.getHeight() * massstab);
            g.drawImage(klein, (targetWidth - breite) / 2, (targetHeight - hoehe) / 2, breite, hoehe, null);
            // Etwas abdunkeln, damit das scharfe Foto davor nicht untergeht.
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRect(0, 0, targetWidth, targetHeight);
            return;
        }
        g.setColor(fuellung == Fuellung.WEISS ? Color.WHITE : Color.BLACK);
        g.fillRect(0, 0, targetWidth, targetHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
