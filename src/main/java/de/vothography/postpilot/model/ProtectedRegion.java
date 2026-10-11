package de.vothography.postpilot.model;

/**
 * Ein horizontaler Bereich des Bildes, durch den keine Slice-Grenze laufen darf —
 * etwa eine Turmspitze oder ein anderes Hauptmotiv.
 *
 * @param label  Bezeichnung für die Oberfläche, z. B. "Turmspitze"
 * @param startX linke Kante des zu schützenden Bereichs
 * @param endX   rechte Kante des zu schützenden Bereichs
 */
public record ProtectedRegion(String label, int startX, int endX) {

    public ProtectedRegion {
        if (endX <= startX) {
            throw new IllegalArgumentException("Schutzbereich '" + label + "' ist leer: " + startX + ".." + endX);
        }
    }

    /** Eine Schnittkante genau auf der Bereichsgrenze schneidet das Motiv noch nicht an. */
    public boolean contains(int boundaryX) {
        return boundaryX > startX && boundaryX < endX;
    }
}
