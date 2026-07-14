package main.java.p2pchat.hadoop;

public class ClientInfo {
    private String clientId;
    private String clientName;
    private String clientIp;
    private int clientPort;
    private long lastAliveTime;

    public ClientInfo(
            String clientId,
            String clientName,
            String clientIp,
            int clientPort,
            long lastAliveTime
    ) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.lastAliveTime = lastAliveTime;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public long getLastAliveTime() {
        return lastAliveTime;
    }

    @Override
    public String toString() {
        return clientId
                + "|"
                + clientName
                + "|"
                + clientIp
                + "|"
                + clientPort
                + "|"
                + lastAliveTime;
    }

    public static ClientInfo fromString(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String[] parts = line.split("\\|");

        if (parts.length != 5) {
            return null;
        }

        try {
            String clientId = parts[0].trim();
            String clientName = parts[1].trim();
            String clientIp = parts[2].trim();
            int clientPort = Integer.parseInt(parts[3].trim());
            long lastAliveTime = Long.parseLong(parts[4].trim());

            return new ClientInfo(
                    clientId,
                    clientName,
                    clientIp,
                    clientPort,
                    lastAliveTime
            );

        } catch (NumberFormatException e) {
            return null;
        }
    }
}