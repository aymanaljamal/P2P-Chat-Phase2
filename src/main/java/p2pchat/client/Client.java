package main.java.p2pchat.client;

import main.java.p2pchat.hadoop.ClientInfo;
import main.java.p2pchat.hadoop.HadoopClientRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Client {

    private  boolean running = true;

    private String clientId;

    private String clientName;

    private int chatPort;

    private ServerSocket chatServerSocket;

    private HadoopClientRegistry registry;

    private final Map<String, String> activeClients = new HashMap<>();

    public Client(
            String clientId,
            String clientName,
            int chatPort,
            HadoopClientRegistry registry
    ) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.chatPort = chatPort;
        this.registry = registry;
    }

    public void start() {
        try {
            startChatServer();
            registerClientInHadoop();
            refreshActiveClientsMap();
            startAliveSender();
            startActiveClientsUpdater();
            handleUserInput();

        } catch (Exception e) {
            System.err.println("Failed to start Client: " + e.getMessage());
            closeResources();
        }
    }

    private void registerClientInHadoop() {
        ClientInfo clientInfo = new ClientInfo(
                clientId,
                clientName,
                getLocalIpAddress(),
                chatPort,
                System.currentTimeMillis()
        );

        registry.registerOrUpdateClient(clientInfo);
    }

    private void startAliveSender() {
        Thread aliveThread = new Thread(() -> {
            while (running) {
                registerClientInHadoop();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        aliveThread.start();
    }

    private void startActiveClientsUpdater() {
        Thread updaterThread = new Thread(() -> {
            while (running) {
                refreshActiveClientsMap();

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        updaterThread.start();
    }

    private void refreshActiveClientsMap() {
        List<ClientInfo> clients = registry.getActiveClients(clientId);

        clearActiveClients();

        if (clients == null || clients.isEmpty()) {
            return;
        }

        for (ClientInfo client : clients) {
            addOrUpdateActiveClient(
                    client.getClientId(),
                    client.getClientName()
            );
        }
    }

    private void addOrUpdateActiveClient(String id, String name) {
        synchronized (activeClients) {
            activeClients.put(id, name);
        }
    }

    private void removeActiveClient(String id) {
        synchronized (activeClients) {
            activeClients.remove(id);
        }
    }

    private void clearActiveClients() {
        synchronized (activeClients) {
            activeClients.clear();
        }
    }

    private void startChatServer() {
        try {
            try {
                chatServerSocket = new ServerSocket(chatPort);
            } catch (IOException e) {
                System.err.println("Port " + chatPort + " is already in use.");
                System.err.println("Choosing an available port automatically...");

                chatServerSocket = new ServerSocket(0);
                chatPort = chatServerSocket.getLocalPort();
            }

            System.out.println("Chat server started on port " + chatPort);

            Thread chatServerThread = new Thread(() -> {
                while (running && chatServerSocket != null && !chatServerSocket.isClosed()) {
                    try {
                        Socket incomingSocket = chatServerSocket.accept();

                        Thread chatHandlerThread = new Thread(() -> {
                            handleIncomingChat(incomingSocket);
                        });

                        chatHandlerThread.start();

                    } catch (IOException e) {
                        if (chatServerSocket != null && !chatServerSocket.isClosed()) {
                            System.err.println("Error accepting chat connection: " + e.getMessage());
                        }
                        break;
                    }
                }
            });

            chatServerThread.start();

        } catch (IOException e) {
            System.err.println("Failed to start chat server: " + e.getMessage());
        }
    }

    private void handleIncomingChat(Socket socket) {
        try {
            BufferedReader chatReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            String message = chatReader.readLine();

            if (message == null || !message.startsWith("CHAT|")) {
                System.err.println("Received invalid chat message.");
                return;
            }

            String[] parts = message.split("\\|", 4);

            if (parts.length != 4) {
                System.err.println("Invalid CHAT message format: " + message);
                return;
            }

            String senderId = parts[1];
            String senderName = parts[2];
            String messageText = parts[3];

            addOrUpdateActiveClient(senderId, senderName);

            System.out.println();
            System.out.println("[" + senderName + " - " + senderId + "]: " + messageText);
            System.out.print("> ");

        } catch (IOException e) {
            System.err.println("Error handling incoming chat: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing chat socket: " + e.getMessage());
            }
        }
    }

    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("Commands:");
        System.out.println("list");
        System.out.println("refresh");
        System.out.println("chat <clientId> <message>");
        System.out.println("exit");

        while (running) {
            System.out.print("> ");

            String input = scanner.nextLine();

            if (input == null || input.trim().isEmpty()) {
                continue;
            }

            input = input.trim();

            if (input.equalsIgnoreCase("list")) {
                printActiveClients();

            } else if (input.equalsIgnoreCase("refresh")) {
                refreshActiveClientsMap();
                printActiveClients();

            } else if (input.startsWith("chat ")) {
                handleChatCommand(input);

            } else if (input.equalsIgnoreCase("exit")) {
                closeResources();
                break;

            } else {
                System.out.println("Unknown command.");
            }
        }
    }

    private void handleChatCommand(String input) {
        String[] parts = input.split(" ", 3);

        if (parts.length < 3) {
            System.out.println("Usage: chat clientId message");
            return;
        }

        String targetClientId = parts[1];
        String chatMessage = parts[2];

        if (isSelf(targetClientId)) {
            System.out.println("You cannot chat with yourself.");
            return;
        }

        ClientInfo targetClient = registry.findClientById(targetClientId);

        if (targetClient == null) {
            removeActiveClient(targetClientId);
            System.out.println("Client not found or inactive.");
            return;
        }

        addOrUpdateActiveClient(
                targetClient.getClientId(),
                targetClient.getClientName()
        );

        sendChatMessage(
                targetClient.getClientIp(),
                targetClient.getClientPort(),
                chatMessage
        );
    }

    private void sendChatMessage(String targetIp, int targetPort, String message) {
        try {
            Socket chatSocket = new Socket(targetIp, targetPort);

            PrintWriter chatWriter = new PrintWriter(chatSocket.getOutputStream(), true);

            String chatMessage = "CHAT|" + clientId + "|" + clientName + "|" + message;

            chatWriter.println(chatMessage);

            chatSocket.close();

            System.out.println("Message sent.");

        } catch (IOException e) {
            System.err.println("Failed to send chat message: " + e.getMessage());
        }
    }

    private void printActiveClients() {
        synchronized (activeClients) {
            if (activeClients.isEmpty()) {
                System.out.println("No active clients.");
                return;
            }

            System.out.println("Active clients:");

            for (Map.Entry<String, String> entry : activeClients.entrySet()) {
                System.out.println(entry.getKey() + " - " + entry.getValue());
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            return "127.0.0.1";
        }
    }

    private boolean isSelf(String id) {
        return clientId != null && clientId.equals(id);
    }

    private void closeResources() {
        running = false;

        try {
            if (chatServerSocket != null && !chatServerSocket.isClosed()) {
                chatServerSocket.close();
            }

        } catch (IOException e) {
            System.err.println("Error closing client resources: " + e.getMessage());
        }
    }
}