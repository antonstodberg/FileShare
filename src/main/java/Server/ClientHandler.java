package Server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;

import Client.UserCommands;

public class ClientHandler extends Thread {

    public static final String INTERNAL_ARGUMENT_MISMATCH =
            "Arguments or params not correct with internal method implementation";

    // could implement Runnable?
    private final Server server;
    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;

    private User user;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;

        try {
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
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
            while (running) {
                input = inputStream.readUTF();
                String[] inputArray = input.trim().split("\\s+");
                UserCommands command = UserCommands.fromCommand(inputArray[0]);
                if (command == null) {
                    printStringToClient(ServerResponse.UNRECOGNIZED_COMMAND);
                    continue;
                }

                switch (command) {
                    case LOGIN:
                        if (user != null) {
                            printStringToClient(ServerResponse.ALREADY_LOGGED_IN);
                            break;
                        }
                        if (inputArray.length < 3) {
                            printStringToClient(ServerResponse.ARGUMENT_MISMATCH);
                            break;
                        }
                        user = server.login(inputArray[1], inputArray[2]);
                        if (user == null) {
                            printStringToClient(ServerResponse.LOGIN_FAILED);
                        } else {
                            printStringToClient(ServerResponse.LOGIN_SUCCESS);
                        }
                        break;

                    case REGISTER:
                        if (inputArray.length < 4) {
                            printStringToClient(ServerResponse.ARGUMENT_MISMATCH);
                            break;
                        }
                        user = server.register(inputArray[1], inputArray[2], inputArray[3]);

                        if (user == null) {
                            printStringToClient(ServerResponse.REGISTER_FAILED);
                        } else {
                            printStringToClient(ServerResponse.REGISTER_SUCCESS);
                        }
                        break;

                    case LIST:
                        if (user == null) {
                            printStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                            break;
                        }
                        printStringToClient(server.listFiles(user));
                        break;

                    case UPLOAD:
                        if (user == null) {
                            printStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                            break;
                        }

                        uploadFile(inputArray);
                        break;

                    case DOWNLOAD:
                        if (user == null) {
                            printStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                            break;
                        }
                        if (inputArray.length < 2) {
                            printStringToClient(ServerResponse.ARGUMENT_MISMATCH);
                            break;
                        }
                        server.downloadFile(user, inputArray[1]);
                        break;

                    case SHARE:
                        if (user == null) {
                            printStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                            break;
                        }
                        if (inputArray.length < 3) {
                            printStringToClient(ServerResponse.ARGUMENT_MISMATCH);
                            break;
                        }
                        server.shareFile(user, inputArray[1], inputArray[2]);
                        break;

                    case LOGOUT:
                        if (user == null) {
                            printStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                            break;
                        }

                        user = null;
                        printStringToClient(ServerResponse.LOGOUT);
                        break;

                    case EXIT:
                        printStringToClient(ServerResponse.EXIT);
                        running = false;
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException ignored) { }

            server.removeClient(this);
        }
    }

    private void printStringToClient(String s) throws IOException {
        outputStream.writeUTF(s);
        outputStream.flush();
    }

    private void uploadFile(String[] inputArray) throws IOException {
        if (inputArray.length < 3) {
            System.err.println(INTERNAL_ARGUMENT_MISMATCH);
            printStringToClient(ServerResponse.UPLOAD_FAILED);
            return;
        }

        try {
            long fileSize = Long.parseLong(inputArray[2]);
            String fileName = inputArray[1];

            boolean success = server.uploadFile(fileName, fileSize, user.getId(), inputStream);

            printStringToClient(success ? ServerResponse.UPLOAD_SUCCESS : ServerResponse.UPLOAD_FAILED);

        } catch (NumberFormatException e) {
            System.err.println(INTERNAL_ARGUMENT_MISMATCH + "\n ClientHandler.uploadFile(String[] inputArray) inputArray[2] -> expected long fileSize but received something else");
            printStringToClient(ServerResponse.UPLOAD_FAILED);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            printStringToClient(ServerResponse.UPLOAD_FAILED);
        }
    }

}
