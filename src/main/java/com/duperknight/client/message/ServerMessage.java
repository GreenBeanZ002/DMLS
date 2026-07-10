package com.duperknight.client.message;

import net.minecraft.text.Text;

public record ServerMessage(Text text, String cleanText, MessageOrigin origin, boolean overlay, long receivedAtNanos) {
}
