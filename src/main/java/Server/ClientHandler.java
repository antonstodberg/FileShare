package Server;

import Protocol.Commands;
import Protocol.Response;
import java.io.*;
import java.net.Socket;
import java.sql.SQLException;

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
                Commands command = Commands.fromString(inputArray[0]);
                if (command == null) {
                    sendToClient(Response.UNRECOGNIZED_COMMAND);
                    continue;
                }

                switch (command) {
                    case LOGIN:
                        if (user != null) {
                            sendToClient(Response.ALREADY_LOGGED_IN);
                            break;
                        }
                        if (inputArray.length < 3) {
                            sendToClient(Response.ARGUMENT_MISMATCH);
                            break;
                        }
                        user = server.login(inputArray[1], inputArray[2]);
                        if (user == null) {
                            sendToClient(Response.LOGIN_FAILED);
                        } else {
                            sendToClient(Response.LOGIN_SUCCESS);
                        }
                        break;

                    case REGISTER:
                        if (inputArray.length < 4) {
                            sendToClient(Response.ARGUMENT_MISMATCH);
                            break;
                        }
                        user = server.register(inputArray[1], inputArray[2], inputArray[3]);

                        if (user == null) {
                            sendToClient(Response.REGISTER_FAILED);
                        } else {
                            sendToClient(Response.REGISTER_SUCCESS);
                        }
                        break;

                    case LIST:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }
                        sendToClient(server.listFiles(user));
                        break;

                    case UPLOAD:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }

                        uploadFile(inputArray);
                        break;

                    case DOWNLOAD:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }

                        downloadFile(inputArray);
                        break;

                    case DELETE:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }
                        delete(inputArray);
                        break;

                    case SHARE:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }
                        share(inputArray);
                        break;

                    case UNSHARE:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }
                        unshare(inputArray);
                        break;

                    case LOGOUT:
                        if (user == null) {
                            sendToClient(Response.USER_NOT_LOGGED_IN);
                            break;
                        }

                        user = null;
                        sendToClient(Response.LOGOUT);
                        break;

                    case EXIT:
                        sendToClient(Response.EXIT);
                        running = false;
                        break;
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println(e.getMessage());
            try {
                sendToClient(e.getMessage());
            } catch (IOException ignored) {}
        } finally {
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException ignored) { }

            server.removeClient(this);
        }
    }

    private void sendToClient(Response response) throws IOException {
        outputStream.writeUTF(response.name());
        outputStream.flush();
    }

    private void sendToClient(String s) throws IOException {
        outputStream.writeUTF(s);
        outputStream.flush();
    }

    private void uploadFile(String[] inputArray) throws IOException {
        if (inputArray.length < 3) {
            System.err.println(INTERNAL_ARGUMENT_MISMATCH);
            sendToClient(Response.UPLOAD_FAILED);
            return;
        }

        try {
            String fileName = inputArray[1];
            long fileSize = Long.parseLong(inputArray[2]);

            boolean success = server.uploadFile(fileName, fileSize, user.getId(), inputStream);

            sendToClient(success ? Response.UPLOAD_SUCCESS : Response.UPLOAD_FAILED);

        } catch (NumberFormatException e) {
            System.err.println(INTERNAL_ARGUMENT_MISMATCH + "\n ClientHandler.uploadFile(String[] inputArray) inputArray[2] -> expected long fileSize but received something else");
            sendToClient(Response.UPLOAD_FAILED_INTERNAL_ERROR);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            sendToClient(Response.UPLOAD_FAILED_INTERNAL_ERROR);
        }
    }

    private void downloadFile(String[] inputArray) throws IOException, SQLException {
        if (inputArray.length < 2) {
            sendToClient(Response.ARGUMENT_MISMATCH);
            return;
        }
        try {
            long fileId = Long.parseLong(inputArray[1]);
            DownloadData data = server.downloadFile(user.getId(), fileId);

            byte[] buffer = new byte[64 * 1024];
            long remaining = data.getSize();

            try (InputStream dataInputStream = data.getInputStream()) {
                outputStream.writeUTF(Response.DOWNLOAD_SUCCESS.name());
                outputStream.writeUTF(data.getFileName());
                outputStream.writeLong(data.getSize());
                outputStream.flush();

                while (remaining > 0) {
                    int bytesToRead = (int) Math.min(remaining, buffer.length);
                    int bytesRead = dataInputStream.read(buffer, 0, bytesToRead);

                    if (bytesRead == -1) {
                        throw new EOFException("File ended unexpectedly");
                    }

                    outputStream.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }

        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendToClient(Response.INCORRECT_FORMAT_ARGUMENT.name());
        } catch (FileNotFoundException | IllegalAccessException e) {
            sendToClient(e.getMessage());
        }
    }

    private void delete(String[] inputArray) throws SQLException, IOException {
        if (inputArray.length < 2) {
            sendToClient(Response.ARGUMENT_MISMATCH);
            return;
        }

        try {
            long fileId = Long.parseLong(inputArray[1]);
            server.deleteFile(user.getId(), fileId);
            sendToClient(Response.DELETE_SUCCESS);

        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendToClient(Response.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException e) {
            sendToClient(e.getMessage());
        }
    }

    private void share(String[] inputArray) throws IOException, SQLException {
        if (inputArray.length < 3) {
            sendToClient(Response.ARGUMENT_MISMATCH);
            return;
        }
        try {
            long fileId = Long.parseLong(inputArray[1]);
            server.shareFile(user.getId(), fileId, inputArray[2]);
            sendToClient(Response.SHARE_SUCCESS);
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendToClient(Response.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException | IllegalArgumentException e) {
            sendToClient(e.getMessage());
        }
    }

    private void unshare(String[] inputArray) throws IOException, SQLException {
        if (inputArray.length < 3) {
            sendToClient(Response.ARGUMENT_MISMATCH);
            return;
        }
        try {
            long fileId = Long.parseLong(inputArray[1]);
            server.unshareFile(user.getId(), fileId, inputArray[2]);
            sendToClient(Response.UNSHARE_SUCCESS);
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendToClient(Response.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException | IllegalArgumentException e) {
            sendToClient(e.getMessage());
        }
    }

}
