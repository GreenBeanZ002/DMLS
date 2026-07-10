package com.duperknight.client.message;

import com.duperknight.client.utils.ChatUtils;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Small, client-thread message router. DMLS-local messages bypass these Fabric receive events. */
public final class ServerMessageRouter {
    private static final long CROSS_EVENT_DUPLICATE_NANOS = 250_000_000L;
    private static final List<Subscription> SUBSCRIPTIONS = new ArrayList<>();
    private static String previousText = "";
    private static MessageOrigin previousOrigin;
    private static long previousAt;
    private static boolean registered;

    private ServerMessageRouter() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> route(message,
                overlay ? MessageOrigin.OVERLAY : MessageOrigin.SERVER_SYSTEM, overlay));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                route(message, MessageOrigin.PLAYER_CHAT, false));
    }

    public static void subscribe(EnumSet<MessageOrigin> origins, Consumer<ServerMessage> consumer) {
        SUBSCRIPTIONS.add(new Subscription(EnumSet.copyOf(origins), Objects.requireNonNull(consumer)));
    }

    static boolean isCrossEventDuplicate(String text, MessageOrigin origin, long now) {
        boolean duplicate = origin != previousOrigin && text.equals(previousText) && now - previousAt <= CROSS_EVENT_DUPLICATE_NANOS;
        previousText = text;
        previousOrigin = origin;
        previousAt = now;
        return duplicate;
    }

    static void resetDuplicateStateForTests() {
        previousText = "";
        previousOrigin = null;
        previousAt = 0;
    }

    private static void route(Text text, MessageOrigin origin, boolean overlay) {
        String clean = ChatUtils.cleanLine(text.getString());
        long now = System.nanoTime();
        if (isCrossEventDuplicate(clean, origin, now)) return;
        ServerMessage message = new ServerMessage(text, clean, origin, overlay, now);
        List.copyOf(SUBSCRIPTIONS).forEach(subscription -> {
            if (subscription.origins.contains(origin)) subscription.consumer.accept(message);
        });
    }

    private record Subscription(EnumSet<MessageOrigin> origins, Consumer<ServerMessage> consumer) {
    }
}
