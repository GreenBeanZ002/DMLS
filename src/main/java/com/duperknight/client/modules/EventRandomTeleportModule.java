package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventProtectScreen;
import com.duperknight.client.gui.modules.EventRandomTeleportScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class EventRandomTeleportModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - RandomTP§8] §7";
    private static final Random RANDOM = new Random();

    public EventRandomTeleportModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.random_teleport.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_PEARL);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.random_teleport.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventRandomTeleportScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    public String teleportToRandomPlayer(MinecraftClient client) {
        if (ClientUtils.isNotConnected(client)) {
            return null;
        }

        List<String> players = new ArrayList<>(ClientUtils.getOnlinePlayerNames(client));
        String selfName = client.getSession().getUsername();
        players.remove(selfName);

        if (players.isEmpty()) {
            return null;
        }

        String target = players.get(RANDOM.nextInt(players.size()));
        client.player.networkHandler.sendChatCommand("vanish");
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.random_teleport.vanish");
        client.player.networkHandler.sendChatCommand("tp " + target);
        return target;
    }
}