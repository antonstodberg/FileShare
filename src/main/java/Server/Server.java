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

            try {
                socket.close();
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

    public User register(String username, String email, String password) throws SQLException {
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        return database.register(username, email, passwordHash);
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

        return sb.toString();
    }

    public List<User> getUsersWithAccessToFile(int ownerId, long fileId) throws FileNotFoundException, IllegalAccessException, SQLException {
        FileRecord file = database.getFile(fileId);
        if (file == null) throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);

        if (file.getOwnerId() != ownerId) {
            throw new IllegalAccessException(ServerResponse.USER_NOT_OWN_FILE);
        }

        return new ArrayList<User>(database.getUsersWithFileAccess(fileId));
    }

    public boolean uploadFile(String fileName,
                           long fileSize,
                           int ownerId,
                           InputStream inputStream) throws IOException, SQLException {

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

    public DownloadData downloadFile(int userId, long fileId) throws SQLException, IOException, IllegalAccessException {
        FileRecord file = database.getFile(fileId);
        if (file == null) throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);

        if (database.hasAccess(userId, fileId)) {
            return new DownloadData(FileStorage.read(fileId), database.getFile(fileId));
        } else {
            throw new IllegalAccessException(ServerResponse.USER_NOT_ACCESS_TO_FILE);
        }
    }

    public void deleteFile(int userId, long fileId)
            throws SQLException, IllegalAccessException, IOException {

        FileRecord file = database.getFile(fileId);

        if (file == null) {
            throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);
        }

        if (file.getOwnerId() != userId) {
            throw new IllegalAccessException(ServerResponse.USER_NOT_OWN_FILE);
        }

        if (!FileStorage.exists(fileId)) {
            database.deleteFile(fileId);
            throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);
        }

        FileStorage.delete(fileId);
        database.deleteFile(fileId);
    }

    public void shareFile(int userId, long fileId, String username) throws SQLException, IllegalAccessException, FileNotFoundException {
        FileRecord file = database.getFile(fileId);

        if (file == null) throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);

        if (file.getOwnerId() != userId) {
            throw new IllegalAccessException(ServerResponse.USER_NOT_OWN_FILE);
        }

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

    public void unshareFile(int userId, long fileId, String username) throws SQLException, IllegalAccessException, FileNotFoundException {
        FileRecord file = database.getFile(fileId);
        if (file == null) throw new FileNotFoundException(ServerResponse.FILE_DOES_NOT_EXIST);

        if (file.getOwnerId() != userId) {
            throw new IllegalAccessException(ServerResponse.USER_NOT_OWN_FILE);
        }

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
