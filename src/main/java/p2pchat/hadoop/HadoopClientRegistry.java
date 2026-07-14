package main.java.p2pchat.hadoop;

import java.util.ArrayList;
import java.util.List;

public class HadoopClientRegistry {

    private static final long CLIENT_TIMEOUT_MS = 8000;

    private final HadoopFileReader fileReader;
    private final HadoopFileWriter fileWriter;


    public HadoopClientRegistry(String clientsFilePath) {
        this.fileReader = new HadoopFileReader(clientsFilePath);
        this.fileWriter = new HadoopFileWriter(clientsFilePath);
    }
    public synchronized void registerOrUpdateClient(ClientInfo newClient) {
        List<ClientInfo> oldClients = fileReader.readAllClients();
        List<ClientInfo> updatedClients = new ArrayList<>();

        boolean updated = false;

        for (ClientInfo currentClient : oldClients) {
            if (!isActive(currentClient)) {
                continue;
            }

            if (currentClient.getClientId().equals(newClient.getClientId())) {
                updatedClients.add(newClient);
                updated = true;
            } else {
                updatedClients.add(currentClient);
            }
        }

        if (!updated) {
            updatedClients.add(newClient);
        }

        fileWriter.rewriteAllClients(updatedClients);
    }
    public synchronized ClientInfo findClientById(String clientId) {
        ClientInfo client = fileReader.findClientById(clientId);

        if (client == null) {
            return null;
        }

        if (!isActive(client)) {
            return null;
        }

        return client;
    }

    public synchronized List<ClientInfo> getActiveClients(String requesterId) {
        List<ClientInfo> clients = fileReader.findActiveClients(CLIENT_TIMEOUT_MS);
        List<ClientInfo> result = new ArrayList<>();

        for (ClientInfo client : clients) {
            if (!isSelf(requesterId, client.getClientId())) {
                result.add(client);
            }
        }

        return result;
    }

    private boolean isActive(ClientInfo client) {
        long currentTime = System.currentTimeMillis();
        long lastAliveTime = client.getLastAliveTime();

        long timeSinceLastAlive = currentTime - lastAliveTime;

        if (timeSinceLastAlive <= CLIENT_TIMEOUT_MS) {
            return true;
        }

        return false;
    }

    private boolean isSelf(String requesterId, String targetId) {
        return requesterId != null && requesterId.equals(targetId);
    }
}