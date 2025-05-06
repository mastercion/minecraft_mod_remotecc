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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class ClientManager {
    private static class SlaveInstance {
        private final String name;
        private final Socket socket;
        private final PrintWriter writer;
        private final BufferedReader reader;
        private final Thread messageThread;

        public SlaveInstance(String name, Socket socket, PrintWriter writer, BufferedReader reader, Thread messageThread) {
            this.name = name;
            this.socket = socket;
            this.writer = writer;
            this.reader = reader;
            this.messageThread = messageThread;
        }

        public String getName() { return name; }
        public Socket getSocket() { return socket; }
        public PrintWriter getWriter() { return writer; }
        public BufferedReader getReader() { return reader; }
        public Thread getMessageThread() { return messageThread; }
    }

    private static ClientMode mode = ClientMode.NONE;
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static final AtomicReference<String> connectedClient = new AtomicReference<>(null);
    private static ConnectionThread connectionThread;
    private static final List<SlaveInstance> connectedSlaves = new ArrayList<>();
    private static final Random random = new Random();
    private static final String[] namePrefixes = {
            "RandomLover", "TechWizard", "PixelPilot", "CodeNinja", "ByteMaster",
            "NetRunner", "DataDiver", "CyberSlave", "BitBender", "LogicLord",
            "ChipChampion", "BinaryBoss", "QuantumQuirk", "DigitalDynamo", "CircuitSage",
            "AlgoAce", "MatrixMaven", "CloudCrawler", "ServerSage", "NetNode",
            "DataDragon", "ByteBaron", "CodeCzar", "BitBaron", "LogicLion",
            "ChipChief", "BinaryBaron", "QuantumKing", "DigitalDuke", "CircuitCzar",
            "AlgoArch", "MatrixMaster", "CloudKing", "ServerSultan", "NetNoble",
            "DataDuke", "ByteBaron", "CodeCzar", "BitBaron", "LogicLord",
            "ChipChampion", "BinaryBoss", "QuantumQuirk", "DigitalDynamo", "CircuitSage"
    };

    private static String generateSlaveName() {
        String prefix = namePrefixes[random.nextInt(namePrefixes.length)];
        int number = random.nextInt(10000);
        return String.format("%s%04d", prefix, number);
    }

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

        // Disconnect all slaves
        for (SlaveInstance slave : connectedSlaves) {
            try {
                slave.getSocket().close();
                slave.getMessageThread().interrupt();
            } catch (IOException e) {
                RemoteCCMod.LOGGER.error("Error closing slave socket", e);
            }
        }
        connectedSlaves.clear();

        connectedClient.set(null);
    }

    public static void sendChatMessageToSlave(String message) {
        if (mode == ClientMode.MASTER) {
            for (SlaveInstance slave : connectedSlaves) {
                slave.getWriter().println("CHAT " + message);
            }
        } else {
            RemoteCCMod.LOGGER.warn("Cannot send chat message: Not in MASTER mode.");
        }
    }

    public static void sendCommandToSlave(String command) {
        if (mode == ClientMode.MASTER) {
            for (SlaveInstance slave : connectedSlaves) {
                slave.getWriter().println("COMMAND " + command);
            }
        } else {
            RemoteCCMod.LOGGER.warn("Cannot send command: Not in MASTER mode.");
        }
    }

    public static void handleIncomingChatMessage(String messageFromMaster) {
        if (MinecraftClient.getInstance().player != null) {
            // If message starts with #, send it directly to chat
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
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket slaveSocket = serverSocket.accept();
                    String slaveAddress = slaveSocket.getInetAddress().getHostAddress();
                    String slaveName = generateSlaveName();

                    PrintWriter slaveWriter = new PrintWriter(slaveSocket.getOutputStream(), true);
                    BufferedReader slaveReader = new BufferedReader(new InputStreamReader(slaveSocket.getInputStream()));

                    Thread messageThread = new Thread(() -> {
                        try {
                            String line;
                            while (!Thread.currentThread().isInterrupted() && (line = slaveReader.readLine()) != null) {
                                processIncomingMessage(line);
                            }
                        } catch (IOException e) {
                            if (!Thread.currentThread().isInterrupted()) {
                                RemoteCCMod.LOGGER.error("Error in slave message thread", e);
                            }
                        }
                    });
                    messageThread.start();

                    SlaveInstance slaveInstance = new SlaveInstance(slaveName, slaveSocket, slaveWriter, slaveReader, messageThread);
                    connectedSlaves.add(slaveInstance);

                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(Text.literal("§7[RemoteCC]: §fSlave [" + slaveName + "] connected from " + slaveAddress), false);
                    }

                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        RemoteCCMod.LOGGER.error("Error accepting connection", e);
                    }
                }
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