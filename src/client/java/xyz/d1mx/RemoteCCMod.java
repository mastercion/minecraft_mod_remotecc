package xyz.d1mx;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            );
        });
    }
}