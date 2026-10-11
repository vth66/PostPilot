package de.vothography.postpilot.model;

/**
 * Ein einzelner Ausschnitt eines Pano-Splits, in Pixelkoordinaten des Originalbildes.
 *
 * @param index  1-basierte Position im Carousel
 * @param x      linke Kante im Originalbild (inklusiv)
 * @param y      obere Kante im Originalbild (inklusiv)
 * @param width  Breite in Pixeln
 * @param height Höhe in Pixeln
 */
public record PanoSlice(int index, int x, int y, int width, int height) {

    public PanoSlice {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Slice " + index + " hat keine positive Größe: " + width + "x" + height);
        }
    }

    /** Rechte Kante (exklusiv) — zugleich die linke Kante des nächsten Slices. */
    public int rightEdge() {
        return x + width;
    }

    public double aspectRatio() {
        return (double) width / height;
    }
}
