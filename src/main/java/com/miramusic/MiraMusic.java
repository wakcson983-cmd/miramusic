package com.miramusic;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MiraMusic implements ModInitializer {

    public static final String MOD_ID = "miramusic";

    /** Jugadores con Mira Music activado. */
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    /** Si se activó para todos (@a), los que entren después también lo reciben. */
    private static volatile boolean allMode = false;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(MiraStatePayload.ID, MiraStatePayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("mira")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("music")
                                .then(CommandManager.argument("targets", EntityArgumentType.players())
                                        .then(CommandManager.literal("on")
                                                .executes(ctx -> run(ctx, true)))
                                        .then(CommandManager.literal("off")
                                                .executes(ctx -> run(ctx, false)))))));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (allMode) {
                ENABLED.add(player.getUuid());
            }
            if (ENABLED.contains(player.getUuid())) {
                ServerPlayNetworking.send(player, new MiraStatePayload(true));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ENABLED.remove(handler.player.getUuid()));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ENABLED.clear();
            allMode = false;
        });
    }

    private static int run(CommandContext<ServerCommandSource> ctx, boolean on) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
        int total = ctx.getSource().getServer().getPlayerManager().getCurrentPlayerCount();

        // Si el selector alcanzó a todos los conectados (@a), se recuerda para los que entren después.
        if (targets.size() >= total) {
            allMode = on;
        }
        for (ServerPlayerEntity player : targets) {
            if (on) {
                ENABLED.add(player.getUuid());
            } else {
                ENABLED.remove(player.getUuid());
            }
            ServerPlayNetworking.send(player, new MiraStatePayload(on));
        }

        final int n = targets.size();
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Mira Music " + (on ? "activado" : "desactivado") + " para " + n + " jugador(es)."), true);
        return n;
    }
}
