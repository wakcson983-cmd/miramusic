package com.miramusic.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Texturas del menú de música y utilidades de dibujo. */
public final class Tex {
    private Tex() {}

    private static Identifier id(String name) {
        return Identifier.of("miramusic", "textures/gui/" + name + ".png");
    }

    public static final Identifier PANEL = id("panel");
    public static final Identifier SLOT = id("slot");
    public static final Identifier HEADPHONES = id("headphones_button");
    public static final Identifier PREV = id("icon_prev");
    public static final Identifier PLAY = id("icon_play");
    public static final Identifier PAUSE = id("icon_pause");
    public static final Identifier NEXT = id("icon_next");
    public static final Identifier CLOSE = id("btn_close");
    public static final Identifier SPEAKER = id("icon_speaker");
    public static final Identifier SPEAKER_MUTE = id("icon_speaker_mute");
    public static final Identifier NOTE = id("note");

    /** Dibuja una textura cuadrada completa escalada al rectángulo indicado. */
    public static void icon(DrawContext c, Identifier tex, int x, int y, int w, int h, int texSize) {
        c.drawTexture(tex, x, y, w, h, 0f, 0f, texSize, texSize, texSize, texSize);
    }

    /** Dibuja un marco de 9 trozos: las esquinas no se estiran, los bordes y el centro sí. */
    public static void nine(DrawContext c, Identifier tex, int x, int y, int w, int h, int corner, int ts) {
        int cs = Math.min(corner, Math.min(w, h) / 2);
        int mw = w - 2 * cs, mh = h - 2 * cs;
        int tm = ts - 2 * corner;
        // esquinas
        c.drawTexture(tex, x, y, cs, cs, 0f, 0f, corner, corner, ts, ts);
        c.drawTexture(tex, x + w - cs, y, cs, cs, ts - corner, 0f, corner, corner, ts, ts);
        c.drawTexture(tex, x, y + h - cs, cs, cs, 0f, ts - corner, corner, corner, ts, ts);
        c.drawTexture(tex, x + w - cs, y + h - cs, cs, cs, ts - corner, ts - corner, corner, corner, ts, ts);
        if (mw > 0) {
            c.drawTexture(tex, x + cs, y, mw, cs, corner, 0f, tm, corner, ts, ts);
            c.drawTexture(tex, x + cs, y + h - cs, mw, cs, corner, ts - corner, tm, corner, ts, ts);
        }
        if (mh > 0) {
            c.drawTexture(tex, x, y + cs, cs, mh, 0f, corner, corner, tm, ts, ts);
            c.drawTexture(tex, x + w - cs, y + cs, cs, mh, ts - corner, corner, corner, tm, ts, ts);
        }
        if (mw > 0 && mh > 0) {
            c.drawTexture(tex, x + cs, y + cs, mw, mh, corner, corner, tm, tm, ts, ts);
        }
    }
}
