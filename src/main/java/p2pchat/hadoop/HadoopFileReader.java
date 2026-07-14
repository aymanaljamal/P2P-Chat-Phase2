package main.java.p2pchat.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HadoopFileReader {

    private final String clientsFilePath;
    private final Configuration configuration;

    public HadoopFileReader(String clientsFilePath) {
        this.clientsFilePath = clientsFilePath;

        this.configuration = new Configuration();
        this.configuration.set("fs.defaultFS", "hdfs://localhost:9000");
    }

    public List<ClientInfo> readAllClients() {
        List<ClientInfo> clients = new ArrayList<>();

        Path path = new Path(clientsFilePath);

        try {
            FileSystem fileSystem = FileSystem.get(configuration);

            if (!fileSystem.exists(path)) {
                return clients;
            }

            try (
                    FSDataInputStream inputStream = fileSystem.open(path);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                    )
            ) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty()) {
                        continue;
                    }

                    ClientInfo clientInfo = ClientInfo.fromString(line);

                    if (clientInfo != null) {
                        clients.add(clientInfo);
                    }
                }
            }

        } catch (FileNotFoundException e) {
            return clients;

        } catch (Exception e) {

        }

        return clients;
    }

    public ClientInfo findClientById(String clientId) {
        List<ClientInfo> allClients = readAllClients();

        for (ClientInfo client : allClients) {
            if (client.getClientId().equals(clientId)) {
                return client;
            }
        }

        return null;
    }

    public List<ClientInfo> findActiveClients(long timeoutMillis) {
        List<ClientInfo> allClients = readAllClients();
        List<ClientInfo> activeClients = new ArrayList<>();

        long currentTime = System.currentTimeMillis();

        for (ClientInfo client : allClients) {
            long timeSinceLastAlive = currentTime - client.getLastAliveTime();

            if (timeSinceLastAlive <= timeoutMillis) {
                activeClients.add(client);
            }
        }

        return activeClients;
    }
}