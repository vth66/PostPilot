package de.vothography.postpilot.model;

/**
 * Instagram-Zielformate. Die Werte sind bewusst hier gebündelt, weil Instagram
 * seine Empfehlungen gelegentlich ändert — dann nur diese Datei anfassen.
 *
 * <p>Pano-Split stand hier früher als viertes Format. Das war falsch: Ein Pano-Split ist
 * kein Zielformat, sondern eine Eigenschaft eines einzelnen Fotos im Carousel. Solange der
 * Wert existierte, konnte die Oberfläche ihn beim Export mitschicken und bekam dann die
 * nutzlose Meldung „nicht breit genug" zurück.
 */
public enum CropFormat {

    FEED_4_5("4:5 Feed", 0.8, 1080, 1350),
    SQUARE_1_1("1:1", 1.0, 1080, 1080),
    STORY_9_16("9:16 Story", 9.0 / 16.0, 1080, 1920);

    private final String label;
    private final double aspectRatio;
    private final int targetWidth;
    private final int targetHeight;

    CropFormat(String label, double aspectRatio, int targetWidth, int targetHeight) {
        this.label = label;
        this.aspectRatio = aspectRatio;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
    }

    public String label() {
        return label;
    }

    /** Breite geteilt durch Höhe. */
    public double aspectRatio() {
        return aspectRatio;
    }

    public int targetWidth() {
        return targetWidth;
    }

    public int targetHeight() {
        return targetHeight;
    }
}
