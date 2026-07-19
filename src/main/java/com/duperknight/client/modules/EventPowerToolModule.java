package com.duperknight.client.modules;

import com.duperknight.client.gui.modules.EventPowerToolScreen;
import com.duperknight.client.session.CommandDispatch;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.duperknight.client.utils.EventPowerToolStorage;
import com.duperknight.client.utils.InputValidators;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EventPowerToolModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - PowerTools§8] §7";
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_COMMAND_LENGTH = 256 - "powertool ".length();

    public EventPowerToolModule() {
        super(StaffDepartment.EVENTS);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.event_powertool.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.REDSTONE_TORCH);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.event_powertool.description"));
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.EVENTS;
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new EventPowerToolScreen(parent, this));
    }

    @Override
    public void register() {
        // No event listeners needed
    }

    public Map<String, String> saved() {
        return EventPowerToolStorage.all();
    }

    /** Saves a named command, without binding it to anything yet. */
    public boolean save(MinecraftClient client, String name, String command) {
        String cleanName = normalizeName(name);
        Optional<String> cleanCommand = normalizeCommand(command);
        if (cleanName.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_powertool.name");
            return false;
        }
        if (cleanCommand.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_powertool.command");
            return false;
        }
        if (!EventPowerToolStorage.save(cleanName, cleanCommand.get())) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.save_failed");
            return false;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.saved", cleanName);
        return true;
    }

    /** Binds a saved command to the currently held item via /powertool. */
    public boolean load(MinecraftClient client, String name) {
        String cleanName = normalizeName(name);
        Optional<String> storedCommand = EventPowerToolStorage.get(cleanName);
        if (storedCommand.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.unknown", cleanName);
            return false;
        }
        Optional<String> command = normalizeCommand(storedCommand.get());
        if (command.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.validation.event_powertool.command");
            return false;
        }
        if (!canRunPrivilegedOperation(client)) return false;

        CommandDispatch dispatch = ClientUtils.dispatchCommand(client, "powertool " + command.get());
        if (dispatch == CommandDispatch.BLOCKED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return false;
        }
        if (dispatch == CommandDispatch.SENT) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.loaded", cleanName, command.get());
        }
        return true;
    }

    public boolean delete(MinecraftClient client, String name) {
        String cleanName = normalizeName(name);
        if (!EventPowerToolStorage.delete(cleanName)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.unknown", cleanName);
            return false;
        }
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.deleted", cleanName);
        return true;
    }

    /** Clears the /powertool binding on the currently held item. */
    public boolean clear(MinecraftClient client) {
        if (!canRunPrivilegedOperation(client)) return false;

        CommandDispatch dispatch = ClientUtils.dispatchCommand(client, "powertool");
        if (dispatch == CommandDispatch.BLOCKED) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return false;
        }
        if (dispatch == CommandDispatch.SENT) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.powertool.cleared");
        }
        return true;
    }

    public static String normalizeName(String name) {
        String clean = name == null ? "" : name.trim();
        return InputValidators.isSafeCommandArgument(clean, MAX_NAME_LENGTH) && clean.indexOf('§') < 0
                ? clean : "";
    }

    public static Optional<String> normalizeCommand(String command) {
        String clean = command == null ? "" : command.trim();
        while (clean.startsWith("/")) clean = clean.substring(1).stripLeading();
        if (!InputValidators.isSafeCommandArgument(clean, MAX_COMMAND_LENGTH) || clean.indexOf('§') >= 0) {
            return Optional.empty();
        }
        return Optional.of(clean);
    }
}
