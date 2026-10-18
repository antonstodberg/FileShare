package Server;

import Protocol.ServerResponse;
import org.springframework.security.crypto.bcrypt.BCrypt;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Server {
    private final static int DEFAULT_PORT = 2000;
    private SSLServerSocket socket;
    private List<ClientHandler> clients = new ArrayList<>();
    private final Database database;

    private volatile boolean shuttingDown = false;

    public Server() { this(DEFAULT_PORT); }
    public Server(int port) {
        database = new Database();
        try {
            socket = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(port);
            socket.setNeedClientAuth(false);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Port: " + port + " is out of range");
            System.exit(1);
        }
    }

    Database getDatabase() {
        return database;
    }

    private void printServerStatus() {
        String host = null;
        try {
            host = socket.getInetAddress().getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        int port = socket.getLocalPort();
        System.out.println(
                "\n*****************************\n"
                + "Server running. \n"
                + "Host: " + host + '\n'
                + "Port: " + port + '\n'
                + "#Connected clients: " + clients.size()
                + "\n*****************************\n"
                + "\nEnter 'exit' to shut down server safely.\n"
        );
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double size = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};

        int unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", size, units[unitIndex]);
    }

    public void shutdown() {
        shuttingDown = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public void start() {
        try {
            printServerStatus();
            startShutdownListener();
            while (true) {
                SSLSocket clientSocket = (SSLSocket) socket.accept();
                ClientHandler ch = new ClientHandler(clientSocket, this);
                addClient(ch);
                ch.start();
            }

        } catch (IOException e) {
            if (socket.isClosed()) {
                System.out.println("Server stopped");
            } else {
                e.printStackTrace();
            }
        } finally {
            closeAllClients();

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void startShutdownListener() {
        Thread shutdownThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);

            while (scanner.hasNextLine()) {
                String command = scanner.nextLine();

                if (command.equalsIgnoreCase("exit")) {
                    shutdown();
                    break;
                }
            }
        });
        shutdownThread.start();
    }

    private void closeAllClients() {
        List<ClientHandler> clientsToClose;

        synchronized (this) {
            clientsToClose = new ArrayList<>(clients);
            clients.clear();
        }

        for (ClientHandler ch : clientsToClose) {
            ch.interrupt();

            try {
                ch.getSocket().close();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void addClient(ClientHandler ch) {
        clients.add(ch);
        printServerStatus();
    }

    public synchronized void removeClient(ClientHandler ch) {
        try { ch.getSocket().close(); } catch (IOException ignored) { }

        clients.remove(ch);

        if (!shuttingDown) {
            printServerStatus();
        }
    }

    /**
     * Validates registration details, hashes the password and creates the user.
     */
    public User register(String username, String email, String password)
            throws SQLException {

        validateRegistration(username, email, password);

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        return database.register(username, email, passwordHash);
    }

    /**
     * Checks registration details.
     *
     * @throws IllegalArgumentException if any registration detail is invalid.
     */
    private void validateRegistration(
            String username, String email, String password) {

        if (!isValidArgument(username)
                || username.codePointCount(0, username.length()) > 50) {
            throw new IllegalArgumentException(
                    "Username must contain 1–50 characters without whitespace."
            );
        }

        if (!isValidArgument(email)
                || email.codePointCount(0, email.length()) > 255) {
            throw new IllegalArgumentException(
                    "Email must contain 1–255 characters without whitespace."
            );
        }

        int at = email.indexOf('@');

        if (at <= 0
                || at != email.lastIndexOf('@')
                || at == email.length() - 1) {
            throw new IllegalArgumentException(
                    "Email must have the format name@domain."
            );
        }

        if (!isValidArgument(password)
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must contain 1–72 UTF-8 bytes without whitespace."
            );
        }
    }

    public User login(String username, String password) throws SQLException {
        User stored_user = database.getUser(username);
        if (stored_user == null) return null;
        if (BCrypt.checkpw(password, stored_user.getPasswordHash())) {
            return stored_user;
        }
        return null;
    }

    public String listFiles(User user) throws SQLException {
        StringBuilder sb = new StringBuilder();

        appendOwnedFiles(sb, user);
        appendSharedFiles(sb, user);

        return sb.toString();
    }

    private void appendOwnedFiles(StringBuilder sb, User user) throws SQLException {
        sb.append("Your files\n");
        sb.append(String.format("%-6s %-25s %-12s %-22s %-30s%n",
                "ID", "NAME", "SIZE", "UPLOADED", "SHARED TO"));

        sb.append(String.format("%-6s %-25s %-12s %-22s %-30s%n",
                "------", "-------------------------", "------------",
                "----------------------", "------------------------------"));

        List<FileRecord> userFiles = database.getFilesByOwner(user.getId());

        for (FileRecord file : userFiles) {
            List<User> sharedUsers;

            try {
                sharedUsers = getUsersWithAccessToFile(user.getId(), file.getId());
            } catch (FileNotFoundException | IllegalAccessException e) {
                sharedUsers = List.of();
            }

            String sharedTo = sharedUsers.isEmpty()
                    ? "-"
                    : sharedUsers.stream()
                    .map(User::getUsername)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("-");

            sb.append(String.format("%-6d %-25s %-12s %-22s %-30s%n",
                    file.getId(),
                    file.getFileName(),
                    formatFileSize(file.getSize()),
                    file.getUploadedAt(),
                    sharedTo));
        }
    }

    private void appendSharedFiles(StringBuilder sb, User user) throws SQLException {
        sb.append("\nShared with you\n");
        sb.append(String.format("%-6s %-25s %-12s %-22s %-20s%n",
                "ID", "NAME", "SIZE", "UPLOADED", "OWNER"));

        sb.append(String.format("%-6s %-25s %-12s %-22s %-20s%n",
                "------", "-------------------------", "------------",
                "----------------------", "--------------------"));

        List<FileRecord> sharedFiles = database.getSharedFiles(user.getId());

        for (FileRecord file : sharedFiles) {
            sb.append(String.format("%-6d %-25s %-12s %-22s %-20s%n",
                    file.getId(),
                    file.getFileName(),
                    formatFileSize(file.getSize()),
                    file.getUploadedAt(),
                    database.getUser(file.getOwnerId()).getUsername()));
        }
    }

    /** Returns the users with shared access after verifying file ownership. */
    public List<User> getUsersWithAccessToFile(int ownerId, long fileId) throws FileNotFoundException, IllegalAccessException, SQLException {

        requireOwnedFile(ownerId, fileId);
        return new ArrayList<User>(database.getUsersWithFileAccess(fileId));
    }

    public boolean uploadFile(String fileName,
                           long fileSize,
                           int ownerId,
                           InputStream inputStream) throws IOException, SQLException {

        validateUpload(fileName, fileSize);

        long fileId = database.addFile(fileName, fileSize, ownerId);

        try {
            FileStorage.save(fileId, inputStream, fileSize);
            return true;

        } catch (IOException e) {
            database.deleteFile(fileId);
            if (FileStorage.exists(fileId)) FileStorage.delete(fileId);
            return false;
        }
    }

    /** Validates the uploaded file's name and declared size. */
    private void validateUpload(String fileName, long fileSize) throws IOException {
        if (fileName == null
                || fileName.isBlank()
                || fileName.codePointCount(0, fileName.length()) > 255
                || fileName.equals(".")
                || fileName.equals("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains(":")
                || fileName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid file name");
        }

        if (fileSize < 0) {
            throw new IOException("File size must not be negative");
        }
    }

    public DownloadData downloadFile(int userId, long fileId) throws SQLException, IOException, IllegalAccessException {
        FileRecord file = database.getFile(fileId);
        if (file == null) throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);

        if (database.hasAccess(userId, fileId)) {
            return new DownloadData(FileStorage.read(fileId), database.getFile(fileId));
        } else {
            throw new IllegalAccessException(ServerResponse.USER_NOT_ACCESS_TO_FILE);
        }
    }

    /** Deletes an owned file from storage and the database. */
    public void deleteFile(int userId, long fileId)
            throws SQLException, IllegalAccessException, IOException {

        requireOwnedFile(userId, fileId);

        if (!FileStorage.exists(fileId)) {
            database.deleteFile(fileId);
            throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);
        }

        FileStorage.delete(fileId);
        database.deleteFile(fileId);
    }

    /** Shares an owned file with another user. */
    public void shareFile(int userId, long fileId, String username) throws SQLException, IllegalAccessException, FileNotFoundException {
        FileRecord file = requireOwnedFile(userId, fileId);

        User userToShare = database.getUser(username);

        if (userToShare == null) {
            throw new IllegalArgumentException(ServerResponse.USER_NOT_FOUND);
        }

        if (file.getOwnerId() == userToShare.getId()) {
            throw new IllegalArgumentException(ServerResponse.CAN_NOT_SHARE_FILE_TO_SELF);
        }

        if (database.hasSharedAccess(userToShare.getId(), fileId)) {
            throw new IllegalArgumentException(ServerResponse.USER_ALREADY_ACCESS_FILE);
        }

        database.shareFileToUser(fileId, userToShare.getId());
    }

    /** Removes another user's shared access to an owned file. */
    public void unshareFile(int userId, long fileId, String username) throws SQLException, IllegalAccessException, FileNotFoundException {
        FileRecord file = requireOwnedFile(userId, fileId);

        User userToUnshare = database.getUser(username);

        if (userToUnshare == null) {
            throw new IllegalArgumentException(ServerResponse.USER_NOT_FOUND);
        }

        if (file.getOwnerId() == userToUnshare.getId()) {
            throw new IllegalArgumentException(ServerResponse.CAN_NOT_SHARE_FILE_TO_SELF);
        }

        if (!database.hasSharedAccess(userToUnshare.getId(), fileId)) {
            throw new IllegalArgumentException(ServerResponse.USER_NOT_ACCESS_TO_FILE);
        }

        database.removeSharedAccessToFile(fileId, userToUnshare.getId());
    }

    /**
     * Checks that a command argument is nonempty and contains no separators
     * or control characters.
     */
    private boolean isValidArgument(String value) {
        return value != null
                && !value.isEmpty()
                && value.codePoints().noneMatch(c ->
                Character.isWhitespace(c)
                        || Character.isSpaceChar(c)
                        || Character.isISOControl(c));
    }

    /**
     * Retrieves a file and verifies ownership.
     *
     * @throws FileNotFoundException if the file does not exist.
     * @throws IllegalAccessException if the user does not own the file.
     */
    private FileRecord requireOwnedFile(int userId, long fileId)
            throws SQLException, FileNotFoundException, IllegalAccessException {

        FileRecord file = database.getFile(fileId);

        if (file == null) {
            throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);
        }

        if (file.getOwnerId() != userId) {
            throw new IllegalAccessException(ServerResponse.USER_NOT_OWN_FILE);
        }

        return file;
    }

    static void main(String[] args) {
        Server server;
        if (args.length > 0) {
            server = new Server(Integer.parseInt(args[0]));
        } else {
            server = new Server();
        }

        server.start();
    }
}