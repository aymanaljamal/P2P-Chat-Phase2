package main.java.p2pchat.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class HadoopFileWriter {

    private static final Object HDFS_WRITE_LOCK = new Object();

    private static final int WRITE_RETRY_COUNT = 5;
    private static final long MIN_RETRY_DELAY_MS = 150;
    private static final long MAX_RETRY_DELAY_MS = 450;

    private final String clientsFilePath;
    private final Configuration configuration;

    public HadoopFileWriter(String clientsFilePath) {
        this.clientsFilePath = clientsFilePath;

        this.configuration = new Configuration();
        this.configuration.set("fs.defaultFS", "hdfs://localhost:9000");

        createFileIfNotExists();
    }

    public void rewriteAllClients(List<ClientInfo> clients) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= WRITE_RETRY_COUNT; attempt++) {
            try {
                rewriteAllClientsOnce(clients);
                return;

            } catch (Exception e) {
                lastException = e;
                sleepRandomBackoff();
            }
        }

        if (lastException != null) {
            System.err.println("HDFS update failed: " + lastException.getMessage());
        } else {
            System.err.println("HDFS update failed.");
        }
    }

    private void rewriteAllClientsOnce(List<ClientInfo> clients) throws Exception {
        synchronized (HDFS_WRITE_LOCK) {
            Path mainPath = new Path(clientsFilePath);
            Path parentPath = mainPath.getParent();

            try (FileSystem fileSystem = FileSystem.newInstance(configuration)) {
                if (parentPath != null && !fileSystem.exists(parentPath)) {
                    fileSystem.mkdirs(parentPath);
                }

                try (
                        BufferedWriter writer = new BufferedWriter(
                                new OutputStreamWriter(
                                        fileSystem.create(mainPath, true),
                                        StandardCharsets.UTF_8
                                )
                        )
                ) {
                    for (ClientInfo client : clients) {
                        writeClient(writer, client);
                    }

                    writer.flush();
                }
            }
        }
    }

    public void createFileIfNotExists() {
        synchronized (HDFS_WRITE_LOCK) {
            Path path = new Path(clientsFilePath);
            Path parentPath = path.getParent();

            try (FileSystem fileSystem = FileSystem.newInstance(configuration)) {
                if (parentPath != null && !fileSystem.exists(parentPath)) {
                    fileSystem.mkdirs(parentPath);
                }

                if (!fileSystem.exists(path)) {
                    try (
                            BufferedWriter writer = new BufferedWriter(
                                    new OutputStreamWriter(
                                            fileSystem.create(path, false),
                                            StandardCharsets.UTF_8
                                    )
                            )
                    ) {
                        writer.flush();
                    }
                }

            } catch (Exception e) {
                System.err.println("Failed to create clients file in HDFS: " + e.getMessage());
            }
        }
    }

    private void writeClient(BufferedWriter writer, ClientInfo client) throws Exception {
        writer.write(client.toString());
        writer.newLine();
    }

    private void sleepRandomBackoff() {
        try {
            long delay = ThreadLocalRandom.current().nextLong(
                    MIN_RETRY_DELAY_MS,
                    MAX_RETRY_DELAY_MS
            );

            Thread.sleep(delay);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}