package io.github.tsaarni.tcpapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpApp {
    private static final Logger logger = LoggerFactory.getLogger(TcpApp.class);


    private static void client(String[] args) {
        if (args.length != 1) {
            logger.error("Usage: client <address:port>");
            System.exit(1);
        }
        String[] parts = args[0].split(":", 2);
        if (parts.length != 2) {
            logger.error("Invalid argument. Expected format: <address:port>");
            System.exit(1);
        }
        String address = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            logger.error("Invalid port: {}", parts[1]);
            System.exit(1);
            return;
        }

        while (true) {
            logger.info("Attempting connection to {}:{}", address, port);
            try (Socket socket = new Socket(address, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                logger.info("Connected to server {}:{}", address, port);
                while (true) {
                    logger.info("Sending: ping");
                    out.println("ping");
                    String response = in.readLine();
                    if (response == null) {
                        logger.warn("Server closed connection");
                        break;
                    }
                    logger.info("Received: {}", response);
                    Thread.sleep(1000);
                }
            } catch (IOException e) {
                logger.warn("Connection error: {}", e.getMessage());
            } catch (InterruptedException e) {
                logger.warn("Sleep interrupted: {}", e.getMessage());
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.warn("Sleep interrupted: {}", e.getMessage());
            }

        }
    }

    private static void server(String[] args) {
        if (args.length != 1) {
            logger.error("Usage: server <address:port>");
            System.exit(1);
        }
        String[] parts = args[0].split(":", 2);
        if (parts.length != 2) {
            logger.error("Invalid argument. Expected format: <address:port>");
            System.exit(1);
        }
        String address = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            logger.error("Invalid port: {}", parts[1]);
            System.exit(1);
            return;
        }
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(address, port));
            logger.info("Server started on {}:{}", address, port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected from {}", clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            logger.warn("Could not start server: {}", e.getMessage());
        }

    }

    private static void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request;
            while ((request = in.readLine()) != null) {
                logger.info("Received from {}: {}", socket.getRemoteSocketAddress(), request);
                String response = "pong";
                out.println(response);
                logger.info("Sent to {}: {}", socket.getRemoteSocketAddress(), response);
            }
            logger.info("Client {} disconnected", socket.getRemoteSocketAddress());
        } catch (IOException e) {
            logger.warn("Error in client handler: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Usage: java -jar tcpapp.jar <client|server> [args...]");
            System.exit(1);
        }
        String mode = args[0];
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        switch (mode) {
            case "client":
                TcpApp.client(subArgs);
                break;
            case "server":
                TcpApp.server(subArgs);
                break;
            default:
                logger.error("Usage: java -jar tcpapp.jar <client|server>");
                System.exit(1);
        }
    }
}
