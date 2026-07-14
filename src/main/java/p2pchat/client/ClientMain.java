package main.java.p2pchat.client;

import main.java.p2pchat.hadoop.HadoopClientRegistry;
import p2pchat.hadoop.HadoopAutoStarter;


import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) {
        HadoopAutoStarter.prepareHadoop();

        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter client ID: ");
        String clientId = scanner.nextLine();

        System.out.print("Enter client name: ");
        String clientName = scanner.nextLine();

        System.out.print("Enter chat port: ");
        int chatPort = Integer.parseInt(scanner.nextLine());

        HadoopClientRegistry registry =
                new HadoopClientRegistry("/p2pchat/clients.txt");

        Client client = new Client(
                clientId,
                clientName,
                chatPort,
                registry
        );

        client.start();
    }
}