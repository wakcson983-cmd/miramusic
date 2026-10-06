package com.miramusic.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * El menú de música: caja del link, miniatura, anterior / pausa / siguiente y volumen.
 * Se usa tanto en el menú grande (pantalla de espera) como en el mini menú del juego.
 */
public final class MusicPanel {
    private final boolean mini;
    /** Caja de texto real (solo cuando está dentro de una pantalla). */
    public TextFieldWidget field;
    public boolean draggingVol;

    private int x, y, w, h;
    public int[] url = new int[4], thumb = new int[4];
    private int[] prev = new int[4], play = new int[4], next = new int[4];
    private int[] vol = new int[4], speaker = new int[4], close = new int[4];
    private int[] setup = null;

    public MusicPanel(boolean mini) {
        this.mini = mini;
    }

    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int mx = Math.max(3, Math.round(w * 0.06f));
        int inW = w - 2 * mx;

        int urlY = y + Math.round(h * 0.045f);
        int urlH = Math.max(12, Math.round(h * 0.094f));
        url = new int[]{x + mx, urlY, inW, urlH};

        int thY = urlY + urlH + Math.round(h * 0.02f);
        int thH = Math.round(h * 0.57f);
        thumb = new int[]{x + mx, thY, inW, thH};

        int cY = thY + thH + Math.round(h * 0.025f);
        int cH = Math.max(10, Math.round(h * 0.09f));
        prev = new int[]{x + Math.round(w * 0.21f) - cH / 2, cY, cH, cH};
        play = new int[]{x + Math.round(w * 0.48f) - cH / 2, cY, cH, cH};
        next = new int[]{x + Math.round(w * 0.74f) - cH / 2, cY, cH, cH};
        close = new int[]{x + w - mx - cH, cY, cH, cH};

        int vH = Math.max(5, Math.round(h * 0.05f));
        int vY = cY + cH + Math.round(h * 0.02f);
        int spk = vH + 3;
        speaker = new int[]{x + mx, vY - 1, spk, spk};
        vol = new int[]{x + mx + spk + 3, vY, inW - spk - 3, vH};
    }

    public int urlX() { return url[0]; }
    public int urlY() { return url[1]; }
    public int urlW() { return url[2]; }
    public int urlH() { return url[3]; }

    // ------------------------------------------------------------ dibujo

    private static void drawFit(DrawContext c, TextRenderer tr, String s, int x, int y, int maxW, int color, boolean center) {
        int tw = tr.getWidth(s);
        float k = tw > maxW ? maxW / (float) tw : 1f;
        float px = x + (center ? (maxW - tw * k) / 2f : 0f);
        float py = y + (1f - k) * 4f;
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(px, py, 0f);
        m.scale(k, k, 1f);
        c.drawText(tr, s, 0, 0, color, false);
        m.pop();
    }

    private void iconButton(DrawContext c, Identifier tex, int[] r, double mx, double my, boolean interactive) {
        if (interactive && MiraLayout.inside(r, mx, my)) {
            c.fill(r[0] - 2, r[1] - 2, r[0] + r[2] + 2, r[1] + r[3] + 2, 0x40FFFFFF);
        }
        Tex.icon(c, tex, r[0], r[1], r[2], r[3], 16);
    }

    public void render(DrawContext c, TextRenderer tr, int mx, int my, boolean interactive) {
        MusicPlayer mp = MusicPlayer.INSTANCE;
        Tools.refresh();
        Tex.nine(c, Tex.PANEL, x, y, w, h, 8, 32);

        // ---- caja del link
        Tex.nine(c, Tex.SLOT, url[0], url[1], url[2], url[3], 3, 16);
        int ty = url[1] + (url[3] - 8) / 2;
        if (field != null) {
            if (field.getText().isEmpty() && !field.isFocused()) {
                drawFit(c, tr, "Link de YouTube + Enter", url[0] + 4, ty, url[2] - 8, 0xFF8A8A8A, false);
            }
        } else {
            String t = MiraScreen.lastText;
            if (t.isEmpty()) {
                drawFit(c, tr, "Link de YouTube + Enter", url[0] + 4, ty, url[2] - 8, 0xFF8A8A8A, false);
            } else {
                drawFit(c, tr, t, url[0] + 4, ty, url[2] - 8, 0xFFFFFFFF, false);
            }
        }

        // ---- miniatura y textos
        Tex.nine(c, Tex.SLOT, thumb[0], thumb[1], thumb[2], thumb[3], 3, 16);
        int ix = thumb[0] + 2, iw = thumb[2] - 4, iy = thumb[1] + 2, bottom = thumb[1] + thumb[3] - 2;
        int h169 = iw * 9 / 16;
        MusicPlayer.Track t = mp.current();
        if (t != null && t.thumb != null && t.tw > 0) {
            int ih = Math.min(iw * t.th / t.tw, bottom - iy);
            c.drawTexture(t.thumb, ix, iy, iw, ih, 0f, 0f, t.tw, t.th, t.tw, t.th);
            iy += ih + 2;
        } else {
            int ns = Math.max(8, Math.min(iw * 45 / 100, h169));
            Tex.icon(c, Tex.NOTE, ix + (iw - ns) / 2, iy + (h169 - ns) / 2, ns, ns, 32);
            iy += h169 + 2;
        }
        if (mp.size() > 0) {
            String cnt = (mp.index() + 1) + "/" + mp.size();
            c.drawText(tr, cnt, ix + 2, thumb[1] + 4, 0xFFFFD84A, true);
        }

        setup = null;
        int lineY = iy;
        if (!Tools.ready()) {
            lineY = wrapText(c, tr, "Faltan herramientas: " + Tools.missingList(), ix, lineY, iw, bottom - 14, 0xFFFFD84A);
            if (Tools.isInstalling()) {
                drawFit(c, tr, Tools.installStatus(), ix, bottom - 10, iw, 0xFF9CFFB4, true);
            } else if (Tools.canAutoInstall()) {
                setup = new int[]{ix + 1, bottom - 13, iw - 2, 12};
                boolean hv = interactive && MiraLayout.inside(setup, mx, my);
                c.fill(setup[0], setup[1], setup[0] + setup[2], setup[1] + setup[3], 0xFF000000);
                c.fill(setup[0] + 1, setup[1] + 1, setup[0] + setup[2] - 1, setup[1] + setup[3] - 1, hv ? 0xFFE6B800 : 0xFFC9972F);
                drawFit(c, tr, "Instalar (1 click)", setup[0] + 2, setup[1] + 2, setup[2] - 4, 0xFF000000, true);
                String st = Tools.installStatus();
                if (!st.isEmpty() && lineY + 9 <= setup[1]) {
                    drawFit(c, tr, st, ix, lineY, iw, 0xFFFF9B9B, false);
                }
            } else {
                wrapText(c, tr, "Instala yt-dlp, ffmpeg y deno (mira el README).", ix, lineY, iw, bottom, 0xFFBBDDFF);
            }
        } else {
            if (t != null && !t.title.isEmpty()) {
                lineY = wrapText(c, tr, t.title, ix, lineY, iw, bottom - 10, 0xFFFFFFFF);
            } else if (t == null) {
                lineY = wrapText(c, tr, "Escribe un link arriba y pulsa Enter.", ix, lineY, iw, bottom - 10, 0xFFBBDDFF);
            }
            String st = mp.status();
            if (!st.isEmpty()) {
                wrapText(c, tr, st, ix, Math.max(lineY, bottom - 19), iw, bottom, 0xFFFFD84A);
            }
        }

        // ---- controles
        iconButton(c, Tex.PREV, prev, mx, my, interactive);
        iconButton(c, mp.isPlaying() ? Tex.PAUSE : Tex.PLAY, play, mx, my, interactive);
        iconButton(c, Tex.NEXT, next, mx, my, interactive);
        if (mini) {
            if (interactive && MiraLayout.inside(close, mx, my)) {
                c.fill(close[0] - 1, close[1] - 1, close[0] + close[2] + 1, close[1] + close[3] + 1, 0x60FFFFFF);
            }
            Tex.icon(c, Tex.CLOSE, close[0], close[1], close[2], close[3], 16);
        }

        // ---- volumen
        float v = mp.volume();
        Tex.icon(c, v <= 0.001f ? Tex.SPEAKER_MUTE : Tex.SPEAKER, speaker[0], speaker[1], speaker[2], speaker[3], 16);
        c.fill(vol[0] - 1, vol[1] - 1, vol[0] + vol[2] + 1, vol[1] + vol[3] + 1, 0xFF000000);
        c.fill(vol[0], vol[1], vol[0] + vol[2], vol[1] + vol[3], 0xFF0B2A12);
        int fw = Math.round(vol[2] * v);
        c.fill(vol[0], vol[1], vol[0] + fw, vol[1] + vol[3], 0xFF00E640);
        c.fill(vol[0], vol[1], vol[0] + fw, vol[1] + 1, 0xFF9CFFB4);
        int kx = vol[0] + Math.max(1, Math.min(vol[2] - 1, fw));
        c.fill(kx - 1, vol[1] - 2, kx + 1, vol[1] + vol[3] + 2, 0xFFFFFFFF);
        if (draggingVol || (interactive && MiraLayout.inside(vol, mx, my))) {
            String pct = Math.round(v * 100) + "%";
            c.drawText(tr, pct, vol[0] + vol[2] - tr.getWidth(pct), vol[1] - 10, 0xFFFFFFFF, true);
        }
    }

    /** Escribe texto en varias líneas; devuelve la Y siguiente. */
    private int wrapText(DrawContext c, TextRenderer tr, String text, int x, int y, int width, int maxY, int color) {
        List<OrderedText> lines = tr.wrapLines(Text.literal(text), width);
        for (OrderedText line : lines) {
            if (y + 9 > maxY) break;
            c.drawText(tr, line, x, y, color, false);
            y += 9;
        }
        return y;
    }

    // ------------------------------------------------------------ entrada

    public boolean isOver(double mx, double my) {
        return MiraLayout.inside(new int[]{x, y, w, h}, mx, my);
    }

    public void setVol(double mx) {
        MusicPlayer.INSTANCE.setVolume((float) ((mx - vol[0]) / vol[2]));
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isOver(mx, my)) return false;
        MusicPlayer mp = MusicPlayer.INSTANCE;
        if (mini && MiraLayout.inside(close, mx, my)) {
            MiraMusicClient.miniOpen = false;
            return true;
        }
        if (setup != null && MiraLayout.inside(setup, mx, my)) {
            Tools.installAsync();
            return true;
        }
        if (MiraLayout.inside(expand(prev), mx, my)) { mp.prev(); return true; }
        if (MiraLayout.inside(expand(play), mx, my)) { mp.togglePause(); return true; }
        if (MiraLayout.inside(expand(next), mx, my)) { mp.next(); return true; }
        int[] vz = {vol[0] - 2, vol[1] - 4, vol[2] + 4, vol[3] + 8};
        if (MiraLayout.inside(vz, mx, my)) {
            draggingVol = true;
            setVol(mx);
            return true;
        }
        return true;
    }

    private static int[] expand(int[] r) {
        return new int[]{r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4};
    }
}
