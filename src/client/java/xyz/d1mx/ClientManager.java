package xyz.d1mx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

public class ClientManager {

    private static ClientMode mode = ClientMode.NONE;
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static final AtomicReference<String> connectedClient = new AtomicReference<>(null);
    private static ConnectionThread connectionThread;

    public static void setMode(ClientMode newMode) {
        disconnect();
        mode = newMode;

        if (mode == ClientMode.MASTER) {
            try {
                serverSocket = new ServerSocket(5678);
                connectionThread = new ConnectionThread();
                connectionThread.start();
            } catch (IOException e) {
                RemoteCCMod.LOGGER.error("Failed to start server", e);
            }
        }
    }

    public static ConnectResult connect(String address) {
        if (mode == ClientMode.NONE) {
            return new ConnectResult(false, "Set mode to MASTER or SLAVE first");
        }

        if (mode == ClientMode.MASTER) {
            return new ConnectResult(false, "MASTER mode cannot connect, it can only accept connections");
        }

        disconnect();

        try {
            String[] parts = address.split(":");
            if (parts.length != 2) {
                return new ConnectResult(false, "Invalid address format. Use ip:port");
            }

            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            clientSocket = new Socket();
            clientSocket.connect(new InetSocketAddress(ip, port), 5000);

            connectedClient.set("MASTER");
            RemoteCCMod.LOGGER.info("Slave connected with MASTER");

            // Start reading Thread
            new Thread(ClientManager::readMessages).start();

            return new ConnectResult(true, "Connected to MASTER at " + address);
        } catch (Exception e) {
            RemoteCCMod.LOGGER.error("Failed to connect", e);
            return new ConnectResult(false, "Failed to connect: " + e.getMessage());
        }
    }

    public static void disconnect() {
        if (connectionThread != null) {
            connectionThread.interrupt();
            connectionThread = null;
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                RemoteCCMod.LOGGER.error("Error closing server socket", e);
            }
            serverSocket = null;
        }

        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException e) {
                RemoteCCMod.LOGGER.error("Error closing client socket", e);
            }
            clientSocket = null;
        }

        connectedClient.set(null);
    }

    public static void sendChatMessageToSlave(String message) {
        if (mode == ClientMode.MASTER && connectionThread != null && connectionThread.getWriter() != null) {
            connectionThread.getWriter().println("CHAT " + message);
        } else {
            RemoteCCMod.LOGGER.warn("Cannot send chat message: Not in MASTER mode or not connected.");
        }
    }

    public static void sendCommandToSlave(String command) {
        if (mode == ClientMode.MASTER && connectionThread != null && connectionThread.getWriter() != null) {
            connectionThread.getWriter().println("COMMAND " + command);
        } else {
            RemoteCCMod.LOGGER.warn("Cannot send command: Not in MASTER mode or not connected.");
        }
    }

    public static void handleIncomingChatMessage(String messageFromMaster) {
        if (MinecraftClient.getInstance().player != null) {
            // React to baritone prefix
            if (messageFromMaster.startsWith("#")) {
                MinecraftClient.getInstance().player.networkHandler.sendChatMessage(messageFromMaster);
            } else {
                MinecraftClient.getInstance().player.sendMessage(Text.literal(messageFromMaster), false);
            }
        }
    }

    public static void handleIncomingCommand(String commandFromMaster) {
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getServer() != null) {
            MinecraftClient.getInstance().getServer().getCommandManager().executeWithPrefix(MinecraftClient.getInstance().getServer().getCommandSource().withEntity(MinecraftClient.getInstance().player), commandFromMaster);
        } else {
            RemoteCCMod.LOGGER.warn("Could not execute command from master. No local server found.");
        }
    }

    private static class ConnectionThread extends Thread {
        private PrintWriter writer;

        public PrintWriter getWriter() {
            return writer;
        }

        @Override
        public void run() {
            try (Socket slave = serverSocket.accept();
                 PrintWriter slaveWriter = new PrintWriter(slave.getOutputStream(), true);
                 BufferedReader slaveReader = new BufferedReader(new InputStreamReader(slave.getInputStream()))) {

                String slaveAddress = slave.getInetAddress().getHostAddress();
                connectedClient.set("SLAVE");
                RemoteCCMod.LOGGER.info("Master connected with SLAVE at " + slaveAddress);

                // Show chat message with slave IP to master
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[RemoteCC]: §fSlave connected from " + slaveAddress), false);
                }

                // Set instance for Writer
                this.writer = slaveWriter;

                String line;
                while (!Thread.currentThread().isInterrupted() && (line = slaveReader.readLine()) != null) {
                    processIncomingMessage(line);
                }

            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    RemoteCCMod.LOGGER.error("Error in connection thread", e);
                }
            } finally {
                disconnect();
            }
        }

        private void processIncomingMessage(String line) {
            if (line.startsWith("CHAT ")) {
                String chatMessage = line.substring(5);
                MinecraftClient.getInstance().execute(() -> handleIncomingChatMessage(chatMessage));
            } else if (line.startsWith("COMMAND ")) {
                String command = line.substring(8);
                MinecraftClient.getInstance().execute(() -> handleIncomingCommand(command));
            } else {
                RemoteCCMod.LOGGER.warn("Unknown message type: " + line);
            }
        }
    }

    private static void readMessages() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                processIncomingMessage(line);
            }

        } catch (IOException e) {
            RemoteCCMod.LOGGER.error("Error reading from server", e);
        } finally {
            disconnect();
        }
    }

    private static void processIncomingMessage(String line) {
        if (line.startsWith("CHAT ")) {
            String chatMessage = line.substring(5);
            MinecraftClient.getInstance().execute(() -> handleIncomingChatMessage(chatMessage));
        } else if (line.startsWith("COMMAND ")) {
            String command = line.substring(8);
            MinecraftClient.getInstance().execute(() -> handleIncomingCommand(command));
        } else {
            RemoteCCMod.LOGGER.warn("Unknown message type: " + line);
        }
    }
}