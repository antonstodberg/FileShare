package Server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;

import Protocol.UserCommands;
import Protocol.ServerResponse;

/** Handles commands and file transfers for one client connection and tracks its logged-in user. */
public class ClientHandler extends Thread {

    public static final String INTERNAL_ARGUMENT_MISMATCH =
            "Arguments or params not correct with internal method implementation";

    private final Server server;
    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;

    private User user;

    /** Associates the client socket with the server and initializes its data streams. */
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

    /** Returns the socket used by this client handler. */
    public Socket getSocket() { return socket; }

    /** Reads commands until the client exits or an error ends the connection. */
    @Override
    public void run() {
        try {

            boolean running = true;

            while (running) {
                String input = inputStream.readUTF();
                running = handleCommand(input);
            }

        } catch (IOException e) {
            System.err.println("Connection or transfer failed: " + e.getMessage());

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());

            try {
                sendStringToClient(ServerResponse.GENERAL_INTERNAL_ERROR);
            } catch (IOException ignored) {
            }
        } finally {
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException ignored) { }

            server.removeClient(this);
        }
    }

    /**
     * Parses one command, checks the session state and invokes its handler.
     *
     * @param input the command received from the client.
     * @return false if the client requests exit, otherwise true.
     * @throws IOException if communication or an upload operation fails.
     * @throws SQLException if an unhandled database operation fails.
     */
    private boolean handleCommand(String input) throws IOException, SQLException {
        if (input.isBlank()) {
            sendStringToClient(ServerResponse.UNRECOGNIZED_COMMAND);
            return true;
        }
        String[] inputArray = input.trim().split("\\s+");
        UserCommands command = UserCommands.fromCommand(inputArray[0]);
        if (command == null) {
            sendStringToClient(ServerResponse.UNRECOGNIZED_COMMAND);
            return true;
        }

        switch (command) {
            case LOGIN:
                if (user != null) {
                    sendStringToClient(ServerResponse.ALREADY_LOGGED_IN);
                    break;
                }
                login(inputArray);
                break;

            case REGISTER:
                if (user != null) {
                    sendStringToClient(ServerResponse.ALREADY_LOGGED_IN);
                    break;
                }
                register(inputArray);
                break;

            case LIST:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }
                sendStringToClient(server.listFiles(user));
                break;

            case UPLOAD:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }

                uploadFile(input.trim().split("\\s+", 2));
                break;

            case DOWNLOAD:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }

                downloadFile(inputArray);
                break;

            case DELETE:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }
                delete(inputArray);
                break;

            case SHARE:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }
                share(inputArray);
                break;

            case UNSHARE:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }
                unshare(inputArray);
                break;

            case LOGOUT:
                if (user == null) {
                    sendStringToClient(ServerResponse.USER_NOT_LOGGED_IN);
                    break;
                }

                user = null;
                sendStringToClient(ServerResponse.LOGOUT);
                break;

            case EXIT:
                sendStringToClient(ServerResponse.EXIT);
                return false;
        }

        return true;
    }

    /** Writes a response using writeUTF and flushes the output stream. */
    private void sendStringToClient(String s) throws IOException {
        outputStream.writeUTF(s);
        outputStream.flush();
    }

    /** Checks login arguments, attempts authentication and sends the result to the client. */
    private void login(String[] inputArray) throws SQLException, IOException {
        if (inputArray.length != 3) {
            sendStringToClient(ServerResponse.ARGUMENT_MISMATCH);
            return;
        }
        user = server.login(inputArray[1], inputArray[2]);
        if (user == null) {
            sendStringToClient(ServerResponse.LOGIN_FAILED);
        } else {
            sendStringToClient(ServerResponse.LOGIN_SUCCESS);
        }
    }

    /**
     * Registers a user and authenticates the current session on success.
     * Sends validation and database errors without ending the command loop.
     *
     * @param inputArray the command, username, email address and password.
     * @throws IOException if the response cannot be sent.
     */
    private void register(String[] inputArray) throws IOException {
        if (inputArray.length != 4) {
            sendStringToClient(ServerResponse.ARGUMENT_MISMATCH);
            return;
        }

        try {
            User registeredUser = server.register(inputArray[1], inputArray[2], inputArray[3]);

            if (registeredUser == null) {
                sendStringToClient(ServerResponse.REGISTER_FAILED);
                return;
            }

            user = registeredUser;
            sendStringToClient(ServerResponse.REGISTER_SUCCESS);

        } catch (IllegalArgumentException e) {
            sendStringToClient(e.getMessage());

        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                sendStringToClient(ServerResponse.USERNAME_OR_EMAIL_TAKEN);
            } else {
                System.err.println("Registration failed: " + e.getMessage());
                sendStringToClient(ServerResponse.REGISTER_FAILED_DB_ERROR);
            }
        }
    }

    /**
     * Parses upload metadata, receives the file and sends a success response.
     * Throws an IOException on failure so that run() closes the connection.
     *
     * @param inputArray the command and the combined file name and size argument.
     * @throws IOException if the arguments are invalid, storage fails,
     *         a database operation fails or communication fails.
     */
    private void uploadFile(String[] inputArray) throws IOException {
        if (inputArray.length != 2) {
            throw new IOException("Invalid upload arguments");
        }

        String arguments = inputArray[1];
        int separator = arguments.lastIndexOf(' ');

        if (separator <= 0 || separator == arguments.length() - 1) {
            throw new IOException("Expected file name and file size");
        }

        String fileName = arguments.substring(0, separator);
        String sizeText = arguments.substring(separator + 1);

        try {
            long fileSize = Long.parseLong(sizeText);

            boolean success = server.uploadFile(
                    fileName, fileSize, user.getId(), inputStream
            );

            if (!success) {
                throw new IOException("Upload could not be completed");
            }

            sendStringToClient(ServerResponse.UPLOAD_SUCCESS);

        } catch (NumberFormatException e) {
            throw new IOException("Invalid upload file size", e);
        } catch (SQLException e) {
            throw new IOException("Database error during upload", e);
        }
    }

    /**
     * Requests an authorized download and sends its metadata and file contents.
     * Sends error responses for invalid IDs, missing files and denied access.
     *
     * @param inputArray the download command and the requested file ID.
     * @throws IOException if an unhandled file read or communication error occurs.
     * @throws SQLException if a database operation fails.
     */
    private void downloadFile(String[] inputArray) throws IOException, SQLException {
        if (inputArray.length != 2) {
            sendStringToClient(ServerResponse.ARGUMENT_MISMATCH);
            return;
        }
        try {
            long fileId = Long.parseLong(inputArray[1]);
            DownloadData data = server.downloadFile(user.getId(), fileId);

            byte[] buffer = new byte[64 * 1024];
            long remaining = data.getSize();

            try (InputStream dataInputStream = data.getInputStream()) {
                outputStream.writeUTF(ServerResponse.DOWNLOAD_SUCCESS);
                outputStream.writeUTF(data.getFileName());
                outputStream.writeLong(data.getSize());
                outputStream.flush();

                while (remaining > 0) {
                    int bytesToRead = (int)Math.min(remaining, buffer.length);
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
            sendStringToClient(ServerResponse.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException e) {
            sendStringToClient(e.getMessage());
        }
    }

    /** Parses a file ID, requests deletion and sends a success or handled error response. */
    private void delete(String[] inputArray) throws SQLException, IOException {
        if (inputArray.length != 2) {
            sendStringToClient(ServerResponse.ARGUMENT_MISMATCH);
            return;
        }

        try {
            long fileId = Long.parseLong(inputArray[1]);
            server.deleteFile(user.getId(), fileId);
            sendStringToClient(ServerResponse.DELETE_SUCCESS);

        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendStringToClient(ServerResponse.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException e) {
            sendStringToClient(e.getMessage());
        }
    }

    /** Parses a sharing request and sends the result of granting another user access. */
    private void share(String[] inputArray) throws IOException, SQLException {
        if (inputArray.length != 3) {
            sendStringToClient(ServerResponse.ARGUMENT_MISMATCH);
            return;
        }
        try {
            long fileId = Long.parseLong(inputArray[1]);
            server.shareFile(user.getId(), fileId, inputArray[2]);
            sendStringToClient(ServerResponse.SHARE_SUCCESS);
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendStringToClient(ServerResponse.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException | IllegalArgumentException e) {
            sendStringToClient(e.getMessage());
        }
    }

    /** Parses an unsharing request and sends the result of removing another user's access. */
    private void unshare(String[] inputArray) throws IOException, SQLException {
        if (inputArray.length != 3) {
            sendStringToClient(ServerResponse.ARGUMENT_MISMATCH);
            return;
        }
        try {
            long fileId = Long.parseLong(inputArray[1]);
            server.unshareFile(user.getId(), fileId, inputArray[2]);
            sendStringToClient(ServerResponse.UNSHARE_SUCCESS);
        } catch (NumberFormatException e) {
            System.err.println(e.getMessage());
            sendStringToClient(ServerResponse.INCORRECT_FORMAT_ARGUMENT);
        } catch (FileNotFoundException | IllegalAccessException | IllegalArgumentException e) {
            sendStringToClient(e.getMessage());
        }
    }

}
