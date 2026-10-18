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

/** Accepts SSL clients and coordinates authentication, file storage and access control. */
public class Server {
    private final static int DEFAULT_PORT = 2000;
    private SSLServerSocket socket;
    private List<ClientHandler> clients = new ArrayList<>();
    private final Database database;

    private volatile boolean shuttingDown = false;

    /** Initializes the server using the default port. */
    public Server() { this(DEFAULT_PORT); }

    /** Checks database connectivity and creates the SSL listening socket on the specified port. */
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

    /** Returns the database component used by this server. */
    Database getDatabase() {
        return database;
    }

    /** Prints the local host name, listening port and number of registered client handlers. */
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

    /** Formats a byte count using powers of 1024 and a suitable size label. */
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

    /** Marks shutdown as requested and closes the listening socket to stop accepting clients. */
    public void shutdown() {
        shuttingDown = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    /** Accepts clients and starts their handlers, then closes client connections when the loop ends. */
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

    /** Starts a separate thread that listens for the server's exit command. */
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

    /** Clears the client list, interrupts the handlers and attempts to close their sockets. */
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

    /** Adds a client handler to the list and prints the server status. */
    public synchronized void addClient(ClientHandler ch) {
        clients.add(ch);
        printServerStatus();
    }

    /** Closes and removes a client handler, printing status unless shutdown was requested. */
    public synchronized void removeClient(ClientHandler ch) {
        try { ch.getSocket().close(); } catch (IOException ignored) { }

        clients.remove(ch);

        if (!shuttingDown) {
            printServerStatus();
        }
    }

    /**
     * Validates registration details, hashes the password and creates a user.
     *
     * @param username the requested username.
     * @param email the user's email address.
     * @param password the plaintext password to hash.
     * @return the created user, or null if the database returns no generated ID.
     * @throws IllegalArgumentException if registration details are invalid.
     * @throws SQLException if the database operation fails.
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

    /**
     * Authenticates a user against the stored password hash.
     *
     * @param username the username to look up.
     * @param password the plaintext password to check.
     * @return the user if authentication succeeds, otherwise null.
     * @throws SQLException if the database lookup fails.
     */
    public User login(String username, String password) throws SQLException {
        User stored_user = database.getUser(username);
        if (stored_user == null) return null;
        if (BCrypt.checkpw(password, stored_user.getPasswordHash())) {
            return stored_user;
        }
        return null;
    }

    /**
     * Builds a textual listing of owned files and files shared with the user.
     *
     * @param user the user whose files should be listed.
     * @return the formatted listing, including both table headings.
     * @throws SQLException if a required database query fails.
     */
    public String listFiles(User user) throws SQLException {
        StringBuilder sb = new StringBuilder();

        appendOwnedFiles(sb, user);
        appendSharedFiles(sb, user);

        return sb.toString();
    }

    /** Appends a table of the user's owned files and their sharing recipients. */
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

    /** Appends a table of files shared with the user and their owners. */
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

    /**
     * Retrieves the sharing recipients of a file after verifying ownership.
     *
     * @param ownerId the ID of the user requesting the recipient list.
     * @param fileId the ID of the file.
     * @return the sharing recipients, or an empty list if the file is not shared.
     * @throws FileNotFoundException if the file has no database record.
     * @throws IllegalAccessException if the requesting user does not own the file.
     * @throws SQLException if a database operation fails.
     */
    public List<User> getUsersWithAccessToFile(int ownerId, long fileId) throws FileNotFoundException, IllegalAccessException, SQLException {

        requireOwnedFile(ownerId, fileId);
        return new ArrayList<User>(database.getUsersWithFileAccess(fileId));
    }

    /**
     * Validates upload metadata, creates a database record and stores the file.
     * If saving fails, attempts to remove the database record and partial file.
     * Does not close the supplied input stream.
     *
     * @param fileName the original file name.
     * @param fileSize the declared number of bytes to receive.
     * @param ownerId the ID of the file owner.
     * @param inputStream the stream supplying the file contents.
     * @return true if saving succeeds, or false if saving fails and cleanup completes.
     * @throws IOException if metadata validation or file cleanup fails.
     * @throws SQLException if record creation or database cleanup fails.
     */
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

    /**
     * Checks that the file name is nonblank, contains at most 255 code points
     * and has no forbidden path or control characters, and that the size is nonnegative.
     *
     * @param fileName the proposed file name.
     * @param fileSize the declared size in bytes.
     * @throws IOException if either value is invalid.
     */
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

    /**
     * Opens a stored file after checking ownership or shared access.
     *
     * @param userId the ID of the user requesting the file.
     * @param fileId the ID of the requested file.
     * @return the file metadata and an open input stream that the caller must close.
     * @throws FileNotFoundException if the database record or stored file is missing.
     * @throws IllegalAccessException if the user has no access to the file.
     * @throws IOException if the stored file cannot be opened.
     * @throws SQLException if a database operation fails.
     */
    public DownloadData downloadFile(int userId, long fileId) throws SQLException, IOException, IllegalAccessException {
        FileRecord file = database.getFile(fileId);
        if (file == null) throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);

        if (database.hasAccess(userId, fileId)) {
            return new DownloadData(FileStorage.read(fileId), database.getFile(fileId));
        } else {
            throw new IllegalAccessException(ServerResponse.USER_NOT_ACCESS_TO_FILE);
        }
    }

    /**
     * Deletes a file owned by the requesting user.
     * If the stored file is already missing, removes its database record
     * and reports the missing file.
     *
     * @param userId the ID of the requesting user.
     * @param fileId the ID of the file to delete.
     * @throws FileNotFoundException if the database record or stored file is missing.
     * @throws IllegalAccessException if the user does not own the file.
     * @throws IOException if deleting the stored file fails.
     * @throws SQLException if a database operation fails.
     */
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

    /**
     * Grants another user shared access to an owned file.
     *
     * @param userId the ID of the requesting owner.
     * @param fileId the ID of the file to share.
     * @param username the recipient's username.
     * @throws FileNotFoundException if the file has no database record.
     * @throws IllegalAccessException if the requesting user does not own the file.
     * @throws IllegalArgumentException if the recipient is unknown, is the owner
     *         or already has shared access.
     * @throws SQLException if a database operation fails.
     */
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

    /**
     * Removes another user's shared access to an owned file.
     *
     * @param userId the ID of the requesting owner.
     * @param fileId the ID of the file.
     * @param username the username whose shared access should be removed.
     * @throws FileNotFoundException if the file has no database record.
     * @throws IllegalAccessException if the requesting user does not own the file.
     * @throws IllegalArgumentException if the target user is unknown, is the owner
     *         or has no shared access.
     * @throws SQLException if a database operation fails.
     */
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
     * Retrieves file metadata and verifies ownership.
     * Does not check whether the physical file exists.
     *
     * @param userId the ID of the user whose ownership should be checked.
     * @param fileId the ID of the file.
     * @return the metadata of the owned file.
     * @throws FileNotFoundException if the file has no database record.
     * @throws IllegalAccessException if the user does not own the file.
     * @throws SQLException if the database lookup fails.
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

    /** Starts the server using an optional port argument. */
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