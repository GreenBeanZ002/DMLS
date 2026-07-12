package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;

/**
 * Utility methods for interacting with the Minecraft client.
 */
public final class ClientUtils {
    private static final String DRY_RUN_PREFIX = "§8[§6DMLS - DryRun§8] §7";

    private ClientUtils() {
    }

    /**
     * Checks if the client is not connected to a server.
     *
     * @param client the Minecraft client
     * @return true if the client is not connected, false otherwise
     */
    public static boolean isNotConnected(MinecraftClient client) {
        return client == null
                || client.player == null
                || client.world == null
                || client.getNetworkHandler() == null
                || !client.getNetworkHandler().isConnectionOpen();
    }

    /**
     * Sends a chat command to the client.
     *
     * @param client the Minecraft client
     * @param command the command to send
     */
    public static boolean sendCommand(MinecraftClient client, String command) {
        if (ServerGuard.check(client).allowed() && client.getNetworkHandler() != null) {
            if (DMLSConfig.dryRun()) {
                ChatUtils.sendTranslatedMessage(client, DRY_RUN_PREFIX, "dmls.chat.dry_run.would_run", "/" + command);
                return true;
            }
            client.getNetworkHandler().sendChatCommand(command);
            return true;
        }
        return false;
    }

    /**
     * Sends a chat message to the server as the player.
     *
     * @param client the Minecraft client
     * @param message the message to send
     */
    public static void sendChatMessage(MinecraftClient client, String message) {
        if (client != null && client.getNetworkHandler() != null) {
            if (DMLSConfig.dryRun()) {
                ChatUtils.sendTranslatedMessage(client, DRY_RUN_PREFIX, "dmls.chat.dry_run.would_say", message);
                return;
            }
            client.getNetworkHandler().sendChatMessage(message);
        }
    }
}
