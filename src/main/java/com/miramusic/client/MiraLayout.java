package com.miramusic.client;

/** Posiciones (en píxeles de interfaz) del botón de audífonos y del menú. Formato: {x, y, ancho, alto}. */
public final class MiraLayout {
    private MiraLayout() {}

    /** Botón rojo junto al título CHAT de la pantalla de espera. */
    public static int[] bigButton(int w, int h) {
        int s = Math.max(16, (int) Math.min(w * 0.077, h * 0.145));
        return new int[]{(int) (w * 0.286), (int) (h * 0.037), s, s};
    }

    /** Menú grande, en el lugar del panel de juegos de la pantalla de espera. */
    public static int[] bigPanel(int w, int h) {
        int ph = (int) (h * 0.641);
        int pw = (int) (ph * 0.673);
        int cx = (int) (w * 0.693);
        return new int[]{cx - pw / 2, (int) (h * 0.075), pw, ph};
    }

    /** Botón de audífonos en el juego (abajo a la derecha). */
    public static int[] miniButton(int w, int h) {
        int s = Math.max(16, Math.min(26, h / 8));
        return new int[]{w - s - 4, h - s - 4, s, s};
    }

    /** Mini menú en el juego (abajo a la derecha). */
    public static int[] miniPanel(int w, int h) {
        int mh = Math.max(60, Math.min(150, h - 8));
        int mw = Math.round(mh * 0.667f);
        return new int[]{w - mw - 4, h - mh - 4, mw, mh};
    }

    public static boolean inside(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
