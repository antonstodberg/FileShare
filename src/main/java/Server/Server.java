package Server;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private final static int DEFAULT_PORT = 2000;
    private ServerSocket socket;
    private List<ClientHandler> clients = new ArrayList<>();
    private Database database = new Database();

    public Server() { this(DEFAULT_PORT); }
    public Server(int port) {
        try {
            socket = new ServerSocket(port);

        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Port: " + port + " is out of range");
            System.exit(1);
        }
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
                "\n*****************************\n" +
                        "Server running. \n" +
                        "Host: " + host + '\n' +
                        "Port: " + port + '\n' +
                        "#Connected clients: " + clients.size() +
                        "\n*****************************\n"
        );
    }

    public void start() {
        try {
            printServerStatus();

            while (true) {
                Socket clientSocket = socket.accept();
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
            try {
                for (Thread t : clients) {
                    t.interrupt();
                }
                for (ClientHandler ch : clients) {
                    ch.getSocket().close();
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void addClient(ClientHandler ch) {
        clients.add(ch);
        printServerStatus();
    }

    public synchronized void removeClient(ClientHandler ch) {
        try { ch.getSocket().close(); } catch (IOException ignored) { }

        String ip = ch.getSocket().getInetAddress().getHostAddress();
        clients.remove(ch);
        printServerStatus();
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
        sb.append(String.format("%-6s %-25s %-12s %-22s%n",
                "ID", "NAME", "SIZE", "UPLOADED"));

        sb.append(String.format("%-6s %-25s %-12s %-22s%n",
                "------", "-------------------------", "------------", "----------------------"));

        List<FileRecord> userFiles = database.getFilesByOwner(user.getId());
        for (FileRecord file : userFiles) {
            sb.append(String.format("%-6d %-25s %-12d %-22s%n",
                    file.getId(),
                    file.getFileName(),
                    file.getSize(),
                    file.getUploadedAt()));
        }

        sb.append("\nShared with you\n");
        sb.append(String.format("%-6s %-25s %-12s %-22s %-20s%n",
                "ID", "NAME", "SIZE", "UPLOADED", "OWNER"));
        sb.append(String.format("%-6s %-25s %-12s %-22s %-20s%n",
                "------", "-------------------------", "------------", "----------------------", "--------------------"));

        List<FileRecord> sharedFiles = database.getSharedFiles(user.getId());
        for (FileRecord file : sharedFiles) {
            sb.append(String.format("%-6d %-25s %-12d %-22s %-20d%n",
                    file.getId(),
                    file.getFileName(),
                    file.getSize(),
                    file.getUploadedAt(),
                    file.getOwnerId()));
        }

        return sb.toString();
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
            throw new IllegalArgumentException("user: " + username + "not found");
        }

        if (database.hasAccess(userToShare.getId(), fileId)) {
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
            throw new IllegalArgumentException("user: " + username + "not found");
        }

        if (!database.hasAccess(userToUnshare.getId(), fileId)) {
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
