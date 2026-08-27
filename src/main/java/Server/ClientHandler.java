package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import Client.UserCommands;

public class ClientHandler extends Thread {
    // could implement Runnable?
    private final Server server;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private User user;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Socket getSocket() { return socket; }

    @Override
    public void run() {
        try {
            String input;
            boolean running = true;
            while ((running && (input = reader.readLine()) != null)) {
                String[] inputArray = input.trim().split("\\s+");
                UserCommands command = UserCommands.fromCommand(inputArray[0]);
                if (command == null) {
                    writer.println(ServerResponse.UNRECOGNIZED_COMMAND.getResponse());
                    continue;
                }

                switch (command) {
                    case LOGIN:
                        if (user != null) {
                            writer.println(ServerResponse.ALREADY_LOGGED_IN.getResponse());
                            break;
                        }
                        if (inputArray.length < 3){
                            writer.println(ServerResponse.ARGUMENT_MISMATCH.getResponse());
                            break;
                        }
                        user = server.login(inputArray[1], inputArray[2]);
                        if (user == null) {
                            writer.println(ServerResponse.LOGIN_FAILED.getResponse());
                        } else {
                            writer.println(ServerResponse.LOGIN_SUCCESS.getResponse());
                        }
                        break;

                    case REGISTER:
                        if (inputArray.length < 4) {
                            writer.println(ServerResponse.ARGUMENT_MISMATCH.getResponse());
                            break;
                        }
                        user = server.register(inputArray[1], inputArray[2], inputArray[3]);

                        if (user == null) {
                            writer.println(ServerResponse.REGISTER_FAILED.getResponse());
                        } else {
                            writer.println(ServerResponse.REGISTER_SUCCESS.getResponse());
                        }
                        break;

                    case LIST:
                        if (user == null) {
                            writer.println(ServerResponse.USER_NOT_LOGGED_IN.getResponse());
                            break;
                        }
                        writer.println(server.listFiles(user));
                        break;

                    case UPLOAD:
                        if (user == null) {
                            writer.println(ServerResponse.USER_NOT_LOGGED_IN.getResponse());
                            break;
                        }
                        if (inputArray.length < 2) {
                            writer.println(ServerResponse.ARGUMENT_MISMATCH.getResponse());
                            break;
                        }
                        server.uploadFile(user, inputArray[1]);
                        break;

                    case DOWNLOAD:
                        if (user == null) {
                            writer.println(ServerResponse.USER_NOT_LOGGED_IN.getResponse());
                            break;
                        }
                        if (inputArray.length < 2) {
                            writer.println(ServerResponse.ARGUMENT_MISMATCH.getResponse());
                            break;
                        }
                        server.downloadFile(user, inputArray[1]);
                        break;

                    case SHARE:
                        if (user == null) {
                            writer.println(ServerResponse.USER_NOT_LOGGED_IN.getResponse());
                            break;
                        }
                        if (inputArray.length < 3) {
                            writer.println(ServerResponse.ARGUMENT_MISMATCH.getResponse());
                            break;
                        }
                        server.shareFile(user, inputArray[1], inputArray[2]);
                        break;

                    case LOGOUT:
                        if (user == null) {
                            writer.println(ServerResponse.USER_NOT_LOGGED_IN.getResponse());
                            break;
                        }

                        user = null;
                        writer.println(ServerResponse.LOGOUT.getResponse());
                        break;

                    case EXIT:
                        writer.println(ServerResponse.EXIT.getResponse());
                        running = false;
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            writer.close();
            try { reader.close(); } catch (IOException ignored) { }
            server.removeClient(this);
        }
    }

}
