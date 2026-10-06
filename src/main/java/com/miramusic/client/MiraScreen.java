package com.miramusic.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * Pantalla del reproductor.
 * - Con parent (la sala de espera): menú grande en el lugar del panel de juegos.
 * - Sin parent (en el juego): pantalla transparente que libera el cursor para usar el mini menú.
 */
public class MiraScreen extends Screen {
    /** Último texto escrito en la caja del link (se conserva entre pantallas). */
    static String lastText = "";

    private final Screen parent;
    private final MusicPanel panel;
    private TextFieldWidget field;
    private int[] btn = new int[4];
    private int[] pr = new int[4];

    public MiraScreen(Screen parent) {
        super(Text.literal("Mira Music"));
        this.parent = parent;
        this.panel = new MusicPanel(parent == null);
    }

    private boolean panelShown() {
        return parent != null || MiraMusicClient.miniOpen;
    }

    @Override
    protected void init() {
        this.clearChildren();
        if (parent != null) {
            btn = MiraLayout.bigButton(width, height);
            pr = MiraLayout.bigPanel(width, height);
        } else {
            btn = MiraLayout.miniButton(width, height);
            pr = MiraLayout.miniPanel(width, height);
        }
        panel.layout(pr[0], pr[1], pr[2], pr[3]);
        field = new TextFieldWidget(this.textRenderer, panel.urlX() + 4, panel.urlY() + (panel.urlH() - 8) / 2,
                panel.urlW() - 8, 10, Text.literal("URL"));
        field.setMaxLength(300);
        field.setDrawsBackground(false);
        field.setText(lastText);
        field.setChangedListener(s -> lastText = s);
        panel.field = field;
        this.addDrawableChild(field);
    }

    @Override public boolean shouldPause() { return false; }

    // ------------------------------------------------------------ dibujo

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        field.visible = panelShown();
        if (parent != null) {
            parent.render(c, -10000, -10000, delta);
            // tapa el panel de juegos de la sala de espera
            c.fill((int) (width * 0.40), (int) (height * 0.085), (int) (width * 0.985), (int) (height * 0.72), 0xFF22B14C);
        }
        if (panelShown()) {
            panel.render(c, this.textRenderer, mouseX, mouseY, true);
        }
        if (parent != null || !MiraMusicClient.miniOpen) {
            Tex.icon(c, Tex.HEADPHONES, btn[0], btn[1], btn[2], btn[3], 32);
            if (MiraLayout.inside(btn, mouseX, mouseY)) {
                c.fill(btn[0], btn[1], btn[0] + btn[2], btn[1] + btn[3], 0x30FFFFFF);
            }
        }
    }

    // ------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && MiraLayout.inside(btn, mx, my) && (parent != null || !MiraMusicClient.miniOpen)) {
            if (parent != null) {
                close();
            } else {
                MiraMusicClient.miniOpen = true;
            }
            return true;
        }
        if (panelShown() && panel.mouseClicked(mx, my, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panel.draggingVol) {
            panel.setVol(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        panel.draggingVol = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (panelShown() && panel.isOver(mx, my)) {
            MusicPlayer mp = MusicPlayer.INSTANCE;
            mp.setVolume(mp.volume() + (float) vertical * 0.05f);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (field.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.setFocused(null);
                return true;
            }
            return field.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || (parent == null && MiraMusicClient.OPEN_KEY != null && MiraMusicClient.OPEN_KEY.matchesKey(keyCode, scanCode))) {
            close();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && panelShown()) {
            this.setFocused(field);
            return true;
        }
        return false;
    }

    private void submit() {
        String text = field.getText().trim();
        if (text.isEmpty()) return;
        if (MusicPlayer.INSTANCE.addAndPlay(text)) {
            field.setText("");
            this.setFocused(null);
        }
    }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    public void tick() {
        if (!MiraMusicClient.enabled) {
            close();
            return;
        }
        if (parent != null && !parentStillActive()) {
            if (client != null) client.setScreen(null);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        panel.draggingVol = false;
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (parent != null) {
            parent.resize(client, width, height);
        }
        super.resize(client, width, height);
    }

    // ------------------------------------------------------------ mod anterior

    private static Field enabledField;
    private static boolean enabledLookup;

    /** Mira (sin depender de él) si la sala de espera del otro mod sigue activada. */
    private boolean parentStillActive() {
        try {
            if (!enabledLookup) {
                enabledLookup = true;
                enabledField = Class.forName("com.esperajuegos.client.EsperaJuegosClient").getField("enabled");
            }
            return enabledField == null || enabledField.getBoolean(null);
        } catch (Throwable t) {
            enabledField = null;
            return true;
        }
    }
}
