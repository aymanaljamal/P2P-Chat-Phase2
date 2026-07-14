package main.java.p2pchat.client;

import javafx.application.Application;
import main.java.p2pchat.client.P2PChatFXClient;
import p2pchat.hadoop.HadoopAutoStarter;

public class P2PChatLauncher {

    public static void main(String[] args) {
        HadoopAutoStarter.prepareHadoop();

        Application.launch(P2PChatFXClient.class, args);
    }
}