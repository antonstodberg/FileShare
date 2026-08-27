package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.bcrypt.BCrypt;

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

    public void uploadFile(User user, String filepath){

    }

    public void downloadFile(User user, String filename) {

    }

    public void shareFile(User user, String filename, String username) {

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
