package com.duperknight.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.regex.Pattern;

/**
 * Utility methods for interacting with the Minecraft chat.
 */
public final class ChatUtils {
    private static final Pattern FORMATTING_CODE = Pattern.compile("§.");

    private ChatUtils() {
    }

    /**
     * Sends a message to the client's chat.
     *
     * @param client the Minecraft client
     * @param message the message to send
     */
    public static void sendClientMessage(MinecraftClient client, String message) {
        sendClientMessage(client, Text.literal(message));
    }

    /**
     * Sends a text component to the client's chat.
     *
     * @param client the Minecraft client
     * @param message the message to send
     */
    public static void sendClientMessage(MinecraftClient client, Text message) {
        if (client != null && client.inGameHud != null) {
            // Add directly to the HUD. ClientPlayerEntity#sendMessage fires GAME receive
            // callbacks and made local DMLS notifications look like server messages.
            client.inGameHud.getChatHud().addMessage(message);
        }
    }

    /** Sends a translated message after a module's formatted chat prefix. */
    public static void sendTranslatedMessage(MinecraftClient client, String prefix, String translationKey, Object... args) {
        sendClientMessage(client, Text.literal(prefix).append(translated(translationKey, args)));
    }

    /** Gray DMLS prose with non-Text placeholders highlighted in gold. */
    public static Text translated(String translationKey, Object... args) {
        Object[] styledArgs = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            styledArgs[index] = args[index] instanceof Text text
                    ? text
                    : Text.literal(String.valueOf(args[index])).formatted(Formatting.GOLD);
        }
        return Text.translatable(translationKey, styledArgs).formatted(Formatting.GRAY);
    }

    /**
     * Cleans a line of text by stripping formatting codes and trimming whitespace.
     *
     * @param line the line to clean
     * @return the cleaned line
     */
    public static String cleanLine(String line) {
        return stripFormatting(line).trim();
    }

    /**
     * Cleans a segment of text by stripping formatting codes.
     *
     * @param line the line to clean
     * @return the cleaned line
     */
    public static String cleanSegment(String line) {
        return stripFormatting(line);
    }

    /**
     * Strips all formatting codes from a string.
     *
     * @param line the line to strip formatting from
     * @return the stripped line
     */
    public static String stripFormatting(String line) {
        return FORMATTING_CODE.matcher(line).replaceAll("");
    }

    /**
     * Generates a separator line for chat messages based on the client's chat width.
     *
     * @param client the Minecraft client
     * @param linePrefix the prefix for the line
     * @return the separator line
     */
    public static String separatorForChatWidth(MinecraftClient client, String linePrefix) {
        int chatWidth = ChatHud.getWidth(client.options.getChatWidth().getValue());
        int prefixWidth = client.textRenderer.getWidth(stripFormatting(linePrefix));
        int dashWidth = Math.max(1, client.textRenderer.getWidth("-"));
        int dashCount = Math.max(3, (chatWidth - prefixWidth) / dashWidth);
        return "-".repeat(dashCount);
    }
}
