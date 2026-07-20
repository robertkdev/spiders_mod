package com.horrormods.spiders.command;

import com.horrormods.spiders.Spiders;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Mod.EventBusSubscriber(modid = Spiders.ModID)
public final class SpiderAutomationCommandRunner {
    public static final String COMMAND_FILE_NAME = "spider-automation-commands.txt";

    private SpiderAutomationCommandRunner() {
    }

    @SubscribeEvent
    public static void runPendingCommands(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        Path commandFile = FMLPaths.GAMEDIR.get().resolve(COMMAND_FILE_NAME);
        if (!Files.isRegularFile(commandFile)) {
            return;
        }

        List<String> commands;
        try {
            commands = Files.readAllLines(commandFile);
            Files.deleteIfExists(commandFile);
        } catch (IOException ex) {
            Spiders.LOGGER.warn("Unable to consume spider automation command file {}", commandFile, ex);
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        CommandSourceStack source = player.createCommandSourceStack().withPermission(4);
        for (String rawCommand : commands) {
            String command = rawCommand.replace("\uFEFF", "").trim();
            if (command.isEmpty() || command.startsWith("#")) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            Spiders.LOGGER.info("spider_automation_command executing={}", command);
            int result = server.getCommands().performPrefixedCommand(source, command);
            Spiders.LOGGER.info("spider_automation_command finished={} result={}", command, result);
        }
    }
}
