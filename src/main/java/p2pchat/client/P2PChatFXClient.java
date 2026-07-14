package main.java.p2pchat.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import main.java.p2pchat.hadoop.ClientInfo;
import main.java.p2pchat.hadoop.HadoopClientRegistry;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Comparator;
import java.util.List;

public class P2PChatFXClient extends Application {

    private static final String CLIENTS_FILE_PATH = "/p2pchat/clients.txt";

    private volatile boolean running = false;

    private String clientId;
    private String clientName;
    private String clientIp;
    private int chatPort;

    private HadoopClientRegistry registry;
    private ServerSocket chatServerSocket;

    private ListView<ClientInfo> clientsListView;
    private TextArea chatArea;
    private TextField messageField;
    private Label statusLabel;
    private Label portLabel;

    @Override
    public void start(Stage stage) {
        stage.setTitle("P2P Chat - Hadoop Phase 2");
        stage.setMinWidth(980);
        stage.setMinHeight(650);

        stage.setScene(buildLoginScene(stage));

        stage.setOnCloseRequest(event -> {
            closeResources();
            Platform.exit();
        });

        stage.show();
    }

    private Scene buildLoginScene(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("""
                -fx-background-color: linear-gradient(to bottom right, #DBEAFE, #F8FAFC);
                """);

        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(430);
        card.setPadding(new Insets(34));
        card.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 28;
                -fx-border-radius: 28;
                -fx-border-color: #BFDBFE;
                -fx-border-width: 1;
                -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.18), 30, 0.20, 0, 12);
                """);

        Label icon = new Label("💬");
        icon.setStyle("""
                -fx-font-size: 46px;
                -fx-background-color: #DBEAFE;
                -fx-background-radius: 22;
                -fx-padding: 12 18 12 18;
                """);

        Label title = new Label("P2P Chat");
        title.setStyle("""
                -fx-font-size: 31px;
                -fx-font-weight: bold;
                -fx-text-fill: #0F172A;
                """);

        Label subtitle = new Label("Phase 2 - Hadoop Client Registry");
        subtitle.setStyle("""
                -fx-font-size: 14px;
                -fx-text-fill: #64748B;
                """);

        TextField idField = createInput("Example: c1");
        TextField nameField = createInput("Example: Ayman");
        TextField portField = createInput("Example: 6001");

        Button connectButton = new Button("🚀 Connect");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setStyle(primaryButtonStyle());

        connectButton.setOnAction(event -> connect(stage, idField, nameField, portField));

        portField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                connect(stage, idField, nameField, portField);
            }
        });

        Label hint = new Label("HDFS file: " + CLIENTS_FILE_PATH);
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        card.getChildren().addAll(
                icon,
                title,
                subtitle,
                createFieldBlock("Client ID", idField),
                createFieldBlock("Client Name", nameField),
                createFieldBlock("Chat Port", portField),
                connectButton,
                hint
        );

        root.setCenter(card);
        BorderPane.setAlignment(card, Pos.CENTER);

        return new Scene(root, 980, 650);
    }

    private void connect(
            Stage stage,
            TextField idField,
            TextField nameField,
            TextField portField
    ) {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String portText = portField.getText().trim();

        if (id.isEmpty() || name.isEmpty() || portText.isEmpty()) {
            showAlert("Missing Data", "Enter client ID, client name, and chat port.");
            return;
        }

        int port;

        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showAlert("Invalid Port", "Chat port must be a number.");
            return;
        }

        try {
            this.clientId = id;
            this.clientName = name;
            this.chatPort = port;
            this.clientIp = getLocalIp();
            this.registry = new HadoopClientRegistry(CLIENTS_FILE_PATH);

       ClientInfo existingClient = registry.findClientById(clientId);

            if (existingClient != null) {
                showAlert(
                        "Duplicate Client ID",
                        "This client ID is already active. Use another ID."
                );
                return;
            }

            startChatServer();

            running = true;

            registerOrUpdateSelf();
            startAliveSender();
            startClientsRefresher();

            stage.setScene(buildChatScene());

            appendSystemMessage("Connected as " + clientName + " (" + clientId + ")");
            appendSystemMessage("IP: " + clientIp);
            appendSystemMessage("Chat port: " + chatPort);
            appendSystemMessage("Hadoop registry file: " + CLIENTS_FILE_PATH);

        } catch (Exception e) {
            closeResources();
            showAlert("Startup Error", e.getMessage());
            e.printStackTrace();
        }
    }

    private Scene buildChatScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F8FAFC;");

        root.setLeft(buildSidebar());
        root.setCenter(buildChatPanel());

        return new Scene(root, 1020, 680);
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.setPrefWidth(310);
        sidebar.setPadding(new Insets(20));
        sidebar.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #E2E8F0;
                -fx-border-width: 0 1 0 0;
                """);

        Label title = new Label("💬 P2P Chat");
        title.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: #0F172A;
                """);

        Label userLabel = new Label("👤 " + clientName + "  •  " + clientId);
        userLabel.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: #64748B;
                """);

        statusLabel = new Label("🟢 Online");
        statusLabel.setStyle("""
                -fx-background-color: #DCFCE7;
                -fx-text-fill: #166534;
                -fx-background-radius: 999;
                -fx-padding: 6 12 6 12;
                -fx-font-size: 12px;
                -fx-font-weight: bold;
                """);

        portLabel = new Label("Port: " + chatPort);
        portLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: #64748B;
                """);

        Label activeTitle = new Label("Active Clients");
        activeTitle.setStyle("""
                -fx-font-size: 15px;
                -fx-font-weight: bold;
                -fx-text-fill: #334155;
                """);

        clientsListView = new ListView<>();
        clientsListView.setPlaceholder(new Label("No active clients"));
        clientsListView.setStyle("""
                -fx-background-color: transparent;
                -fx-control-inner-background: transparent;
                -fx-border-color: transparent;
                """);

        clientsListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ClientInfo item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                Label avatar = new Label("👤");
                avatar.setStyle("""
                        -fx-font-size: 18px;
                        -fx-background-color: #DBEAFE;
                        -fx-background-radius: 14;
                        -fx-padding: 8;
                        """);

                Label name = new Label(item.getClientName());
                name.setStyle("""
                        -fx-font-size: 14px;
                        -fx-font-weight: bold;
                        -fx-text-fill: #0F172A;
                        """);

                Label details = new Label(item.getClientId() + " • " + item.getClientIp() + ":" + item.getClientPort());
                details.setStyle("""
                        -fx-font-size: 11px;
                        -fx-text-fill: #64748B;
                        """);

                VBox texts = new VBox(3, name, details);

                HBox row = new HBox(12, avatar, texts);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10));
                row.setStyle("""
                        -fx-background-color: #F8FAFC;
                        -fx-background-radius: 16;
                        -fx-border-color: #E2E8F0;
                        -fx-border-radius: 16;
                        """);

                setGraphic(row);
                setText(null);
                setStyle("-fx-background-color: transparent; -fx-padding: 5 0 5 0;");
            }
        });

        Button refreshButton = new Button("🔄 Refresh");
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.setStyle(secondaryButtonStyle());
        refreshButton.setOnAction(event -> refreshClientsList());

        Button exitButton = new Button("🚪 Exit");
        exitButton.setMaxWidth(Double.MAX_VALUE);
        exitButton.setStyle(dangerButtonStyle());
        exitButton.setOnAction(event -> {
            closeResources();
            Platform.exit();
        });

        sidebar.getChildren().addAll(
                title,
                userLabel,
                statusLabel,
                portLabel,
                new Separator(),
                activeTitle,
                clientsListView,
                refreshButton,
                exitButton
        );

        VBox.setVgrow(clientsListView, Priority.ALWAYS);

        return sidebar;
    }

    private VBox buildChatPanel() {
        VBox chatPanel = new VBox(14);
        chatPanel.setPadding(new Insets(20));
        chatPanel.setStyle("-fx-background-color: #F8FAFC;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16));
        header.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 22;
                -fx-border-color: #E2E8F0;
                -fx-border-radius: 22;
                """);

        Label headerIcon = new Label("📨");
        headerIcon.setStyle("""
                -fx-font-size: 24px;
                -fx-background-color: #DBEAFE;
                -fx-background-radius: 16;
                -fx-padding: 8 12 8 12;
                """);

        Label title = new Label("Messages");
        title.setStyle("""
                -fx-font-size: 21px;
                -fx-font-weight: bold;
                -fx-text-fill: #0F172A;
                """);

        Label subtitle = new Label("Select an active client, write a message, then send.");
        subtitle.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: #64748B;
                """);

        VBox headerText = new VBox(3, title, subtitle);
        header.getChildren().addAll(headerIcon, headerText);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("""
                -fx-font-size: 14px;
                -fx-text-fill: #0F172A;
                -fx-control-inner-background: white;
                -fx-background-color: white;
                -fx-background-radius: 22;
                -fx-border-color: #E2E8F0;
                -fx-border-radius: 22;
                -fx-padding: 12;
                """);

        HBox inputBar = new HBox(10);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(14));
        inputBar.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 22;
                -fx-border-color: #E2E8F0;
                -fx-border-radius: 22;
                """);

        messageField = new TextField();
        messageField.setPromptText("Write your message...");
        messageField.setStyle("""
                -fx-background-color: #F8FAFC;
                -fx-background-radius: 16;
                -fx-border-color: #CBD5E1;
                -fx-border-radius: 16;
                -fx-padding: 12;
                -fx-font-size: 14px;
                """);

        Button sendButton = new Button("Send ➜");
        sendButton.setStyle(primaryButtonStyle());
        sendButton.setOnAction(event -> sendMessageToSelectedClient());

        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendMessageToSelectedClient();
            }
        });

        inputBar.getChildren().addAll(messageField, sendButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        chatPanel.getChildren().addAll(header, chatArea, inputBar);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        return chatPanel;
    }

    private void startChatServer() throws IOException {
        try {
            chatServerSocket = new ServerSocket(chatPort);
        } catch (IOException e) {
            chatServerSocket = new ServerSocket(0);
            chatPort = chatServerSocket.getLocalPort();
        }

        Thread serverThread = new Thread(() -> {
            while (running && chatServerSocket != null && !chatServerSocket.isClosed()) {
                try {
                    Socket socket = chatServerSocket.accept();

                    Thread handlerThread = new Thread(() -> handleIncomingChat(socket));
                    handlerThread.setDaemon(true);
                    handlerThread.start();

                } catch (IOException e) {
                    if (running) {
                        appendSystemMessage("Chat server error: " + e.getMessage());
                    }
                }
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleIncomingChat(Socket socket) {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                )
        ) {
            String message = reader.readLine();

            if (message == null || !message.startsWith("CHAT|")) {
                appendSystemMessage("Invalid chat message received.");
                return;
            }

            String[] parts = message.split("\\|", 4);

            if (parts.length != 4) {
                appendSystemMessage("Invalid CHAT message format.");
                return;
            }

            String senderId = parts[1];
            String senderName = parts[2];
            String text = parts[3];

            appendIncomingMessage(senderName, senderId, text);

        } catch (IOException e) {
            appendSystemMessage("Receive error: " + e.getMessage());

        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendMessageToSelectedClient() {
        ClientInfo selectedClient = clientsListView.getSelectionModel().getSelectedItem();

        if (selectedClient == null) {
            showAlert("No Client Selected", "Select a client from the left list first.");
            return;
        }

        String text = messageField.getText().trim();

        if (text.isEmpty()) {
            return;
        }

        messageField.clear();

        Thread senderThread = new Thread(() -> {
            ClientInfo freshClient = registry.findClientById(selectedClient.getClientId());

            if (freshClient == null) {
                appendSystemMessage("Client is no longer active: " + selectedClient.getClientId());
                refreshClientsList();
                return;
            }

            try (
                    Socket socket = new Socket(freshClient.getClientIp(), freshClient.getClientPort());
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String chatMessage =
                        "CHAT|" + clientId + "|" + clientName + "|" + text;

                writer.println(chatMessage);

                appendOutgoingMessage(freshClient.getClientName(), text);

            } catch (IOException e) {
                appendSystemMessage("Failed to send message: " + e.getMessage());
            }
        });

        senderThread.setDaemon(true);
        senderThread.start();
    }

    private void startAliveSender() {
        Thread aliveThread = new Thread(() -> {
            while (running) {
                registerOrUpdateSelf();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        aliveThread.setDaemon(true);
        aliveThread.start();
    }

    private void startClientsRefresher() {
        Thread refreshThread = new Thread(() -> {
            while (running) {
                refreshClientsList();

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        refreshThread.setDaemon(true);
        refreshThread.start();
    }

    private void registerOrUpdateSelf() {
        if (registry == null) {
            return;
        }

        ClientInfo self = new ClientInfo(
                clientId,
                clientName,
                clientIp,
                chatPort,
                System.currentTimeMillis()
        );

        registry.registerOrUpdateClient(self);
    }

    private void refreshClientsList() {
        if (registry == null || clientId == null) {
            return;
        }

        try {
            List<ClientInfo> clients = registry.getActiveClients(clientId);

            clients.sort(Comparator.comparing(ClientInfo::getClientId));

            Platform.runLater(() -> {
                if (clientsListView != null) {
                    clientsListView.getItems().setAll(clients);
                }
            });

        } catch (Exception e) {
            appendSystemMessage("Failed to refresh clients: " + e.getMessage());
        }
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void appendIncomingMessage(String senderName, String senderId, String text) {
        Platform.runLater(() -> {
            chatArea.appendText("\n");
            chatArea.appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            chatArea.appendText("📩 " + senderName + " (" + senderId + ")\n");
            chatArea.appendText(text + "\n");
        });
    }

    private void appendOutgoingMessage(String targetName, String text) {
        Platform.runLater(() -> {
            chatArea.appendText("\n");
            chatArea.appendText("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            chatArea.appendText("📤 Me → " + targetName + "\n");
            chatArea.appendText(text + "\n");
        });
    }

    private void appendSystemMessage(String text) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText("\n⚙ " + text + "\n");
            }
        });
    }

    private VBox createFieldBlock(String labelText, TextField field) {
        Label label = new Label(labelText);
        label.setStyle("""
                -fx-font-size: 13px;
                -fx-font-weight: bold;
                -fx-text-fill: #334155;
                """);

        return new VBox(7, label, field);
    }

    private TextField createInput(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle("""
                -fx-background-color: #F8FAFC;
                -fx-background-radius: 16;
                -fx-border-color: #CBD5E1;
                -fx-border-radius: 16;
                -fx-padding: 12;
                -fx-font-size: 14px;
                """);

        return field;
    }

    private String primaryButtonStyle() {
        return """
                -fx-background-color: #2563EB;
                -fx-text-fill: white;
                -fx-background-radius: 16;
                -fx-padding: 12 18 12 18;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-cursor: hand;
                """;
    }

    private String secondaryButtonStyle() {
        return """
                -fx-background-color: #EFF6FF;
                -fx-text-fill: #1D4ED8;
                -fx-background-radius: 16;
                -fx-padding: 11 16 11 16;
                -fx-font-size: 13px;
                -fx-font-weight: bold;
                -fx-cursor: hand;
                """;
    }

    private String dangerButtonStyle() {
        return """
                -fx-background-color: #FEF2F2;
                -fx-text-fill: #DC2626;
                -fx-background-radius: 16;
                -fx-padding: 11 16 11 16;
                -fx-font-size: 13px;
                -fx-font-weight: bold;
                -fx-cursor: hand;
                """;
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void closeResources() {
        running = false;

        try {
            if (chatServerSocket != null && !chatServerSocket.isClosed()) {
                chatServerSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing chat server: " + e.getMessage());
        }
    }
}