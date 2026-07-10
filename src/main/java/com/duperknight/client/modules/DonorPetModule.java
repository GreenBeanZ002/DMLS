package com.duperknight.client.modules;

import com.duperknight.client.gui.DonorPetScreen;
import com.duperknight.client.utils.ChatUtils;
import com.duperknight.client.utils.ClientUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DonorPetModule extends DMLSModule {
    private static final String PREFIX = "§8[§6DMLS - DonorPet§8] §7";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Map<String, String> PET_PERMISSIONS = new LinkedHashMap<>();

    static {
        PET_PERMISSIONS.put("blackcrow", "mcpets.elitemountvol6blackcrow");
        PET_PERMISSIONS.put("griffon", "mcpets.elitemountvol6whitegriffon");
        PET_PERMISSIONS.put("axolotl", "mcpets.elitemountvol6pinkaxolottle");
        PET_PERMISSIONS.put("yak", "mcpets.elitemountvol6whiteyak");
        PET_PERMISSIONS.put("dog", "mcpets.elitemountvol6huskydog");
    }

    public DonorPetModule() {
        super(StaffRank.ADMIN);
    }

    /** Returns the names of all available pets. */
    public static List<String> pets() {
        return List.copyOf(PET_PERMISSIONS.keySet());
    }

    @Override
    public Text displayName() {
        return Text.translatable("dmls.module.donor_pet.name");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BONE);
    }

    @Override
    public List<Text> description() {
        return List.of(Text.translatable("dmls.module.donor_pet.description"));
    }

    @Override
    public void openScreen(MinecraftClient client, Screen parent) {
        client.setScreen(new DonorPetScreen(parent, this));
    }

    @Override
    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RequiredArgumentBuilder<FabricClientCommandSource, String> ign = ClientCommandManager.argument("ign", StringArgumentType.word());
            for (String pet : PET_PERMISSIONS.keySet()) {
                ign.then(ClientCommandManager.literal(pet).executes(context -> {
                    submit(context.getSource().getClient(), StringArgumentType.getString(context, "ign").trim(), pet);
                    return 1;
                }));
            }
            dispatcher.register(ClientCommandManager.literal("donorpet").then(ign));
        });
    }

    /** Gives the pet permission to the given player. The command and GUI both call this method. */
    public void submit(MinecraftClient client, String ign, String pet) {
        if (!hasRequiredRank(client)) {
            return;
        }

        if (!USERNAME.matcher(ign).matches()) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.common.invalid_ign");
            return;
        }

        String permission = PET_PERMISSIONS.get(pet);
        if (permission == null) {
            ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.donor_pet.unknown", pet, String.join(", ", pets()));
            return;
        }

        ClientUtils.sendCommand(client, "lp user %s permission set %s true".formatted(ign, permission));
        ChatUtils.sendTranslatedMessage(client, PREFIX, "dmls.chat.donor_pet.success", ign, pet, permission);
    }
}
