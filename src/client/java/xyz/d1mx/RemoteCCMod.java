package xyz.d1mx;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mojang.text2speech.Narrator.LOGGER;

public class RemoteCCMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("remotecc");
    public static final String MOD_ID = "remotecc";

    @Override
    public void onInitializeClient() {
        LOGGER.info("RemoteCC Mod initialized on client");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("rcc")
                    .then(ClientCommandManager.literal("MASTER").executes(context -> {
                        // Assuming ClientManager and ClientMode are defined elsewhere in your code
                        ClientManager.setMode(ClientMode.MASTER);
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§a[RemoteCC] Mode set to MASTER"), false);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("SLAVE").executes(context -> {
                        // Assuming ClientManager and ClientMode are defined elsewhere in your code
                        ClientManager.setMode(ClientMode.SLAVE);
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§a[RemoteCC] Mode set to SLAVE"), false);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("connect")
                            .then(ClientCommandManager.argument("address", StringArgumentType.string())
                                    .executes(context -> {
                                        String address = StringArgumentType.getString(context, "address");
                                        // Assuming ClientManager and ConnectResult are defined elsewhere in your code
                                        ConnectResult result = ClientManager.connect(address);
                                        if (result.isSuccess()) {
                                            MinecraftClient.getInstance().player.sendMessage(Text.literal("§a[RemoteCC] " + result.getMessage()), false);
                                        } else {
                                            MinecraftClient.getInstance().player.sendMessage(Text.literal("§c[RemoteCC] " + result.getMessage()), false);
                                        }
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("disconnect").executes(context -> {
                        // Assuming ClientManager is defined elsewhere in your code
                        ClientManager.disconnect();
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§a[RemoteCC] Disconnected"), false);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("chat")
                            .then(ClientCommandManager.argument("slave", StringArgumentType.string())
                                    .suggests((context, builder) -> {
                                        builder.suggest("@a");
                                        builder.suggest("all");
                                        for (String slaveName : ClientManager.getConnectedSlaveNames()) {
                                            builder.suggest(slaveName);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String slaveName = StringArgumentType.getString(context, "slave");
                                                String message = StringArgumentType.getString(context, "message");

                                                if (slaveName.equals("@a") || slaveName.equals("all")) {
                                                    ClientManager.sendChatMessageToAllSlaves(message);
                                                    MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[RemoteCC -> ALL]: §f" + message), false);
                                                } else {
                                                    boolean sent = ClientManager.sendChatMessageToSlave(slaveName, message);
                                                    if (sent) {
                                                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[RemoteCC -> " + slaveName + "]: §f" + message), false);
                                                    } else {
                                                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§c[RemoteCC] Slave " + slaveName + " not found"), false);
                                                    }
                                                }
                                                return 1;
                                            }))))
                    .then(ClientCommandManager.literal("command")
                            .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String command = StringArgumentType.getString(context, "command");
                                        // Hier würde der Befehl über ClientManager an den Slave gesendet
                                        ClientManager.sendCommandToSlave(command);
                                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[RemoteCC -> Slave (Command)]: §f/" + command), false);
                                        return 1;
                                    })))
            );
        });
    }

    public static void handleIncomingChatMessage(String messageFromMaster) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[Master -> RemoteCC]: §f" + messageFromMaster), false);
        }
    }

    public static void handleIncomingCommand(String commandFromMaster) {
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getServer() != null) {
            MinecraftClient.getInstance().getServer().getCommandManager().executeWithPrefix(MinecraftClient.getInstance().getServer().getCommandSource().withEntity(MinecraftClient.getInstance().player), commandFromMaster);
        } else {
            LOGGER.warn("Could not execute command from master. No local server found.");
        }
    }
}