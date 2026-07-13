package com.duperknight.client;

import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DMLSClientCommandTreeTest {
    @Test
    void allDmlsCommandsAreRegisteredAtTheRoot() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();
        Set<String> actual = root.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());

        assertEquals(Set.of(
                "activity", "alerts", "alts", "brb", "chatlog", "co", "containers", "demowave",
                "dnd", "donorpet", "dryrun", "greet", "greeter", "griefs", "help", "lands", "loc",
                "members", "prefix", "promowave", "punish", "rank", "say", "uuid", "xray"
        ), actual);
    }

    @Test
    void commandsFollowingPrefixAreNotNestedInsideIt() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();
        CommandNode<FabricClientCommandSource> prefix = root.getChild("prefix");

        assertEquals(Set.of("ign"),
                prefix.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet()));
    }

    @Test
    void greeterAndDndCanRunWithoutARequiredArgument() {
        CommandNode<FabricClientCommandSource> root = new DMLSClient().buildDmlsCommand().build();

        assertNotNull(root.getChild("greeter").getCommand());
        assertNotNull(root.getChild("dnd").getCommand());
    }
}
