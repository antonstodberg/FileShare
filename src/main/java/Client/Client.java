package Client;

import Server.ServerResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private final static String DEFAULT_HOST = "127.0.0.1";
    private final static int DEFAULT_PORT = 2000;
    private final static String WELCOME_MESSAGE = "FILESHARE\n"
            + UserCommands.LOGIN + " <username> <password> -> log in to already registered user\n"
            + UserCommands.REGISTER + " <username> <email> <password> -> register new user and log in";
    private final static String ENTER_COMMAND = "Enter command:\n"
            + UserCommands.LIST + " -> list all files on server owned or shared with you\n"
            + UserCommands.UPLOAD + " <filepath> -> upload a local file to the server\n"
            + UserCommands.DOWNLOAD + " <filename> -> download file from server to computer\n"
            + UserCommands.SHARE + " <filename> <username> -> share file from your account to the specified user\n"
            + UserCommands.LOGOUT + " -> log out current user\n"
            + UserCommands.EXIT + " -> close program";

    private Socket socket = new Socket();
    private Scanner scanner;
    private PrintWriter writer;
    private BufferedReader reader;

    public Client() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public Client(String host) {
        this(host, DEFAULT_PORT);
    }

    public Client(String host, int port) {
        try {
           socket.connect(new InetSocketAddress(host, port), 5000);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public void printConnectionInfo() {
        System.out.println(
                        "\n*****************************\n" +
                        "Connection established. \n" +
                        "Host: " + socket.getInetAddress().getHostName() + '\n' +
                        "Port: " + socket.getPort() + '\n' +
                        "*****************************\n"
        );
    }

    private void loginLoop() {
        while (true) {
            System.out.println(WELCOME_MESSAGE);
            System.out.print("> ");
            String command = scanner.nextLine();

            writer.println(command);

            String response = reader.readLine();
            System.out.println(response);

            if (response.equals(ServerResponse.LOGIN_SUCCESS.getResponse())
                    || response.equals(ServerResponse.REGISTER_SUCCESS.getResponse())) {
                break;
            }
        }
    }

    private void commandLoop() {
        System.out.println(ENTER_COMMAND);
    }

    static void main(String[] args) {
        Client client = switch (args.length) {
            case 2 -> new Client(args[0], Integer.parseInt(args[1]));
            case 1 -> new Client(args[0]);
            default -> new Client();
        };
        client.printConnectionInfo();

        client.loginLoop();

        client.commandLoop();

        System.out.println(ENTER_COMMAND);
    }
}
