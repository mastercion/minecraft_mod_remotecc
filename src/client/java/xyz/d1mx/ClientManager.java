package xyz.d1mx;

import java.io.IOException;
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

    private static class ConnectionThread extends Thread {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted() && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket slave = serverSocket.accept();
                    String slaveAddress = slave.getInetAddress().getHostAddress();
                    connectedClient.set("SLAVE");

                    // Log the connection instead of using client methods
                    RemoteCCMod.LOGGER.info("Master connected with SLAVE at " + slaveAddress);

                    // Wait until the connection is closed
                    try {
                        while (slave.isConnected() && !slave.isClosed()) {
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException e) {
                        break;
                    } finally {
                        slave.close();
                    }

                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        RemoteCCMod.LOGGER.error("Error in connection thread", e);
                    }
                    break;
                }
            }
        }
    }
}
