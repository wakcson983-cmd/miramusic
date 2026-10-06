package com.miramusic.client;

import com.miramusic.MiraStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

public class MiraMusicClient implements ClientModInitializer {

    private static final String WAITING_SCREEN = "com.esperajuegos.client.WaitingScreen";

    /** Mira Music activado por el servidor para este jugador. */
    public static volatile boolean enabled = false;
    /** El mini menú del juego está abierto. */
    public static boolean miniOpen = false;
    public static KeyBinding OPEN_KEY;

    private static final Map<Screen, Object> TOKENS = new WeakHashMap<>();
    private static final MusicPanel HUD_PANEL = new MusicPanel(true);

    @Override
    public void onInitializeClient() {
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.miramusic.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.miramusic"));

        ClientPlayNetworking.registerGlobalReceiver(MiraStatePayload.ID, (payload, context) -> {
            enabled = payload.enabled();
            if (!enabled) {
                MusicPlayer.INSTANCE.stopAll();
                miniOpen = false;
                MinecraftClient mc = context.client();
                if (mc.currentScreen instanceof MiraScreen ms) {
                    ms.close();
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            enabled = false;
            miniOpen = false;
            MusicPlayer.INSTANCE.stopAll();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MusicPlayer.INSTANCE.stopAll());

        // Tecla para liberar el cursor y usar el mini menú dentro del juego
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.wasPressed()) {
                if (enabled && mc.player != null && mc.currentScreen == null) {
                    mc.setScreen(new MiraScreen(null));
                }
            }
        });

        // Botón de audífonos y mini menú sobre el juego
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!enabled || mc.currentScreen != null || mc.options.hudHidden) return;
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            if (miniOpen) {
                int[] r = MiraLayout.miniPanel(w, h);
                HUD_PANEL.layout(r[0], r[1], r[2], r[3]);
                HUD_PANEL.render(ctx, mc.textRenderer, -1, -1, false);
            } else {
                int[] b = MiraLayout.miniButton(w, h);
                Tex.icon(ctx, Tex.HEADPHONES, b[0], b[1], b[2], b[3], 32);
            }
        });

        // Botón de audífonos en la sala de espera del mod anterior (sin depender de él)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!screen.getClass().getName().equals(WAITING_SCREEN)) return;
            final Object token = new Object();
            TOKENS.put(screen, token);

            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
                if (!enabled || TOKENS.get(s) != token) return;
                int[] b = MiraLayout.bigButton(s.width, s.height);
                Tex.icon(ctx, Tex.HEADPHONES, b[0], b[1], b[2], b[3], 32);
                if (MiraLayout.inside(b, mouseX, mouseY)) {
                    ctx.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x30FFFFFF);
                }
            });

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                if (!enabled || TOKENS.get(s) != token) return true;
                int[] b = MiraLayout.bigButton(s.width, s.height);
                if (button == 0 && MiraLayout.inside(b, mouseX, mouseY)) {
                    MinecraftClient.getInstance().setScreen(new MiraScreen(s));
                    return false;
                }
                return true;
            });
        });
    }
}
