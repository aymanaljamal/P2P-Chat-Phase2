package p2pchat.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Map;

public class HadoopAutoStarter {

    private static final String HADOOP_HOME =
            "C:\\Users\\ayman\\OneDrive\\Desktop\\Distributed\\P2P_Chat_Phase2\\hadoop-3.4.3";

    private static final String HDFS_URI = "hdfs://localhost:9000";

    private static final String CLIENTS_FILE = "/p2pchat/clients.txt";

    /*
        خليه false.
        لو خليته true كل client جديد رح يمسح clients.txt
        وهذا بخرب الشغل لما تفتح أكثر من client.
     */
    private static final boolean RESET_CLIENTS_FILE_ON_START = false;

    private HadoopAutoStarter() {
    }

    public static void prepareHadoop() {
        try {
            setupHadoopEnvironment();

            System.out.println("Starting Hadoop DFS...");
            startDfs();

            System.out.println("Waiting for HDFS...");
            waitUntilHdfsReady();

            System.out.println("Preparing clients file...");
            prepareClientsFile();

            System.out.println("Hadoop is ready.");

        } catch (Exception e) {
            System.err.println("Failed to prepare Hadoop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupHadoopEnvironment() {
        System.setProperty("hadoop.home.dir", HADOOP_HOME);
    }

    private static void startDfs() throws Exception {
        File startDfsFile = new File(HADOOP_HOME + "\\sbin\\start-dfs.cmd");

        if (!startDfsFile.exists()) {
            throw new RuntimeException("start-dfs.cmd not found at: " + startDfsFile.getAbsolutePath());
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                startDfsFile.getAbsolutePath()
        );

        processBuilder.directory(new File(HADOOP_HOME));
        applyHadoopEnvironment(processBuilder.environment());

        Process process = processBuilder.start();

        printProcessOutput(process);

        process.waitFor();
    }

    private static void waitUntilHdfsReady() throws Exception {
        int maxAttempts = 25;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (isHdfsReady()) {
                return;
            }

            System.out.println("HDFS not ready yet... attempt " + attempt + "/" + maxAttempts);
            Thread.sleep(1000);
        }

        throw new RuntimeException("HDFS did not become ready.");
    }

    private static boolean isHdfsReady() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    HADOOP_HOME + "\\bin\\hdfs.cmd",
                    "dfs",
                    "-ls",
                    "/"
            );

            processBuilder.directory(new File(HADOOP_HOME));
            applyHadoopEnvironment(processBuilder.environment());

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            return exitCode == 0;

        } catch (Exception e) {
            return false;
        }
    }

    private static void prepareClientsFile() throws Exception {
        Configuration configuration = new Configuration();
        configuration.set("fs.defaultFS", HDFS_URI);

        FileSystem fileSystem = FileSystem.get(new URI(HDFS_URI), configuration);

        Path folderPath = new Path("/p2pchat");
        Path clientsPath = new Path(CLIENTS_FILE);

        if (!fileSystem.exists(folderPath)) {
            fileSystem.mkdirs(folderPath);
        }

        if (RESET_CLIENTS_FILE_ON_START) {
            if (fileSystem.exists(clientsPath)) {
                fileSystem.delete(clientsPath, false);
            }

            deleteTempFiles(fileSystem, folderPath);
        }

        if (!fileSystem.exists(clientsPath)) {
            fileSystem.create(clientsPath, false).close();
        }

        fileSystem.close();
    }

    private static void deleteTempFiles(FileSystem fileSystem, Path folderPath) throws Exception {
        if (!fileSystem.exists(folderPath)) {
            return;
        }

        var statuses = fileSystem.listStatus(folderPath);

        for (var status : statuses) {
            String fileName = status.getPath().getName();

            if (fileName.startsWith("clients.txt.tmp.")) {
                fileSystem.delete(status.getPath(), false);
            }
        }
    }

    private static void applyHadoopEnvironment(Map<String, String> environment) {
        environment.put("HADOOP_HOME", HADOOP_HOME);
        environment.put("hadoop.home.dir", HADOOP_HOME);

        String oldPath = environment.getOrDefault("PATH", "");
        environment.put(
                "PATH",
                HADOOP_HOME + "\\bin;" + HADOOP_HOME + "\\sbin;" + oldPath
        );
    }

    private static void printProcessOutput(Process process) {
        Thread outputThread = new Thread(() -> {
            try (
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream())
                    )
            ) {
                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println("[HADOOP] " + line);
                }

            } catch (Exception ignored) {
            }
        });

        Thread errorThread = new Thread(() -> {
            try (
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream())
                    )
            ) {
                String line;

                while ((line = reader.readLine()) != null) {
                    System.err.println("[HADOOP ERROR] " + line);
                }

            } catch (Exception ignored) {
            }
        });

        outputThread.setDaemon(true);
        errorThread.setDaemon(true);

        outputThread.start();
        errorThread.start();
    }
}