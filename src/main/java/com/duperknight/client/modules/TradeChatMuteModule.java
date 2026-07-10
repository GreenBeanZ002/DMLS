package com.duperknight.client.modules;

import com.duperknight.client.gui.TradeChatMuteScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.DMLSConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

/** Client-side filter for trade chat messages that operators cannot mute normally. */
public final class TradeChatMuteModule extends DMLSModule {
    public TradeChatMuteModule() {
        super(StaffRank.ADMIN);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.trade_chat.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BARRIER);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.trade_chat.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new TradeChatMuteScreen(parent, this));
    }

    @Override
    public void register() {
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> !shouldHide(message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !shouldHide(message));
    }

    private boolean shouldHide(Text message) {
        return DMLSConfig.tradeChatMuted()
                && DMLSConfig.staffRank().isAtLeast(StaffRank.ADMIN)
                && startsWithTradePrefix(ChatUtils.cleanLine(message.getString()));
    }

    static boolean startsWithTradePrefix(String message) {
        return message.startsWith("[T]");
    }
}
