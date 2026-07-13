package com.duperknight.client.modules;

import com.duperknight.DMLS;
import com.duperknight.client.gui.modules.LocationsScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Saves named locations client-side and teleports back to them on click. */
public final class LocationsModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - Locations§8] §7";
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("dmls-locations.properties");
    private static final int MAX_NAME_LENGTH = 32;

    private final Map<String, SavedLocation> locations = new LinkedHashMap<>();

    /** One saved location. */
    public record SavedLocation(String world, int x, int y, int z) {
    }

    public LocationsModule() {
        super(StaffRank.HELPER);
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.locations.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.FILLED_MAP);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.locations.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new LocationsScreen(parent, this));
    }

    @Override
    public void register() {
        load();
    }

    /** Returns the names of all saved locations. */
    public List<String> names() {
        return List.copyOf(locations.keySet());
    }

    /** Returns a snapshot of all saved locations. */
    public Map<String, SavedLocation> entries() {
        return Map.copyOf(locations);
    }

    /** Saves the player's current position under the given name. */
    public void save(MinecraftClient client, String name) {
        if (!hasRequiredRank(client)) {
            return;
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.invalid_name", MAX_NAME_LENGTH);
            return;
        }

        if (ClientUtils.isNotConnected(client)) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
            return;
        }

        BlockPos pos = client.player.getBlockPos();
        String world = client.world.getRegistryKey().getValue().toString();
        boolean existed = locations.put(trimmed, new SavedLocation(world, pos.getX(), pos.getY(), pos.getZ())) != null;
        persist();
        ChatUtils.sendTranslatedMessage(client, PREFIX,
                existed ? "dmls.chat.locations.updated" : "dmls.chat.locations.saved",
                trimmed, pos.getX() + " " + pos.getY() + " " + pos.getZ(), world);
    }

    /** Teleports to the given saved location. */
    public void teleport(MinecraftClient client, String name) {
        if (!hasRequiredRank(client)) {
            return;
        }

        SavedLocation location = locations.get(name.trim());
        if (location == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.unknown", name.trim());
            return;
        }

        if (ClientUtils.sendCommand(client, "tp %s %s %s".formatted(location.x(), location.y(), location.z()))) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.teleporting",
                    name.trim(), location.x() + " " + location.y() + " " + location.z(), location.world());
        } else {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.command.not_sent");
        }
    }

    /** Deletes the given saved location. */
    public void delete(MinecraftClient client, String name) {
        if (locations.remove(name.trim()) == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.unknown", name.trim());
            return;
        }
        persist();
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.deleted", name.trim());
    }

    /** Lists all saved locations as clickable chat lines. */
    public void list(MinecraftClient client) {
        if (locations.isEmpty()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.empty");
            return;
        }

        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.locations.header", locations.size());
        locations.forEach((name, location) -> {
            Text nameText = Text.literal(name).formatted(Formatting.GOLD).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/dmls loc tp " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.locations.tp_hover", name))));
            Text deleteText = Text.translatable("dmls.chat.locations.delete_button").formatted(Formatting.RED).styled(style -> style
                    .withClickEvent(new ClickEvent.RunCommand("/dmls loc del " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("dmls.chat.locations.delete_hover", name))));
            ChatUtils.sendClientMessage(client, Text.literal("§8• ").append(nameText)
                    .append(Text.literal(" §8(" + location.x() + ", " + location.y() + ", " + location.z()
                            + " in " + location.world() + ") "))
                    .append(deleteText));
        });
    }

    private void load() {
        locations.clear();
        if (!Files.exists(PATH)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            properties.load(in);
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to read {}", PATH, e);
            return;
        }

        for (String name : properties.stringPropertyNames()) {
            String[] parts = properties.getProperty(name, "").split(";");
            if (parts.length != 4) {
                continue;
            }
            try {
                locations.put(name, new SavedLocation(parts[0],
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void persist() {
        Properties properties = new Properties();
        locations.forEach((name, location) -> properties.setProperty(name,
                "%s;%s;%s;%s".formatted(location.world(), location.x(), location.y(), location.z())));
        try (OutputStream out = Files.newOutputStream(PATH)) {
            properties.store(out, "DMLS saved locations");
        } catch (IOException e) {
            DMLS.LOGGER.warn("Failed to save {}", PATH, e);
        }
    }
}
