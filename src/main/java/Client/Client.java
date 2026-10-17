package Client;

import Protocol.ServerResponse;
import Protocol.UserCommands;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

public class Client {
    private final static String DEFAULT_HOST = "127.0.0.1";
    private final static int DEFAULT_PORT = 2000;

    private final static String C_WELCOME_MESSAGE = "\nFILESHARE\n"
            + UserCommands.LOGIN.getCommand()
            + " <username> <password> -> log in to already registered user\n"
            + UserCommands.REGISTER.getCommand()
            + " <username> <email> <password> -> register new user and log in";

    private final static String C_ENTER_COMMAND = "\nEnter command:\n"
            + UserCommands.LIST.getCommand()
            + " -> list all files on server owned or shared with you\n"
            + UserCommands.UPLOAD.getCommand()
            + " <filepath> -> upload a local file to the server\n"
            + UserCommands.DOWNLOAD.getCommand()
            + " <fileId> -> download file from server to computer\n"
            + UserCommands.DELETE.getCommand()
            + " <fileId> -> delete file from server\n"
            + UserCommands.SHARE.getCommand()
            + " <fileId> <username> -> share file from your account to the specified user\n"
            + UserCommands.UNSHARE.getCommand()
            + " <fileId> <username> -> remove that users access to file\n"
            + UserCommands.LOGOUT.getCommand()
            + " -> log out current user\n"
            + UserCommands.EXIT.getCommand()
            + " -> close program";

    private final static String M_SERVER_LOST_CONNECTION = "Lost connection to server";
    private final static String M_FNF_OR_DOES_NOT_EXIST = "Path does not exist, or is not a valid file";
    private final static String M_ARGUMENT_MISMATCH = "Incorrect number of arguments.";
    private final static String M_DOWNLOAD_SUCCESS = "File was downloaded to your FileShare/Downloads folder";
    private final static String M_NOT_A_VALID_COMMAND = "Not a valid command.";

    private SSLSocket socket;
    private final Scanner scanner = new Scanner(System.in);
    private DataOutputStream outputStream;
    private DataInputStream inputStream;

    private String currentUser = "";

    private static final Path DOWNLOAD_PATH =
            Path.of(System.getProperty("user.dir"), "Downloads");

    static {
        try {
            Files.createDirectories(DOWNLOAD_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Could not create download directory", e);
        }
    }

    public Client() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public Client(String host) {
        this(host, DEFAULT_PORT);
    }

    public Client(String host, int port) {
        try {
            socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private void printConnectionInfo() {
        System.out.println(
                        "\n*****************************\n"
                        + "Connection established. \n"
                        + "Host: " + socket.getInetAddress().getHostName() + '\n'
                        + "Port: " + socket.getPort() + '\n'
                        + "*****************************"
        );
    }

    private String readResponse() throws IOException {
        try {
            return inputStream.readUTF();
        } catch (EOFException e) {
            System.err.println(M_SERVER_LOST_CONNECTION);
            shutdownClient();
        }
        return null;
    }

    private void loginLoop() {
        while (true) {
            System.out.println(C_WELCOME_MESSAGE);
            System.out.print("> ");
            String command = scanner.nextLine();
            if (command.isBlank()) {
                System.err.println(M_NOT_A_VALID_COMMAND);
                continue;
            }
            String[] inputArray = command.trim().split("\\s+");

            if (UserCommands.fromCommand(inputArray[0]) == null) {
                System.err.println(M_NOT_A_VALID_COMMAND);
                continue;
            }

            try {
                outputStream.writeUTF(command);
                outputStream.flush();
                String response = readResponse();
                System.out.println(response);

                if (response.equals(ServerResponse.EXIT)) shutdownClient();

                if (response.equals(ServerResponse.LOGIN_SUCCESS)
                    || response.equals(ServerResponse.REGISTER_SUCCESS)) {
                    currentUser = inputArray[1];
                    break;
                }

            } catch (IOException e) {
                System.err.println(e.getMessage());
                shutdownClient();
            }
        }
    }

    private void commandLoop() {
        while (true) {
            try {
                System.out.println("\nFILESHARE\nLogged in as: " + currentUser + C_ENTER_COMMAND);
                System.out.print("> ");

                String command = scanner.nextLine();
                if (command.isBlank()) {
                    System.err.println(M_NOT_A_VALID_COMMAND);
                    continue;
                }
                String[] inputArray = command.trim().split("\\s+");
                UserCommands commandType = UserCommands.fromCommand(inputArray[0]);

                if (commandType == null) {
                    System.err.println(M_NOT_A_VALID_COMMAND);
                    continue;
                }

                String response = switch (commandType) {
                    case UPLOAD -> uploadFile(command.trim().split("\\s+", 2));

                    case DOWNLOAD -> downloadFile(inputArray);

                    default -> {
                        outputStream.writeUTF(command);
                        outputStream.flush();
                        yield readResponse();
                    }
                };

                if (response == null) {
                    System.err.println(M_SERVER_LOST_CONNECTION);
                    shutdownClient();
                }

                System.out.println(response);

                if (response.equals(ServerResponse.EXIT)) {
                    shutdownClient();
                }

                if (response.equals(ServerResponse.LOGOUT)) {
                    currentUser = "";
                    return;
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
                shutdownClient();
            }
        }
    }

    private String uploadFile(String[] inputArray) throws IOException {

        if (inputArray.length != 2) return M_ARGUMENT_MISMATCH;

        String filePath = inputArray[1];

        if (filePath.length() >= 2
                && filePath.startsWith("\"")
                && filePath.endsWith("\"")) {
            filePath = filePath.substring(1, filePath.length() - 1);
        }

        if (filePath.isBlank()) {
            return M_FNF_OR_DOES_NOT_EXIST;
        }

        Path path;

        try {
            path = Path.of(filePath);
        } catch (java.nio.file.InvalidPathException e) {
            return M_FNF_OR_DOES_NOT_EXIST;
        }

        if (!Files.isRegularFile(path)) {
            return M_FNF_OR_DOES_NOT_EXIST;
        }

        String filename = path.getFileName().toString();
        long fileSize = Files.size(path);

        if (filename == null
                || filename.isBlank()
                || filename.equals(".")
                || filename.equals("..")
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains(":")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid file name");
        }

        if (fileSize < 0) {
            throw new IOException("Invalid file size");
        }

        outputStream.writeUTF(UserCommands.UPLOAD.getCommand() + " " + filename + " " + fileSize);

        System.out.println("Uploading...");

        try (InputStream fileIn = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
        }

        return readResponse();
    }

    private String downloadFile(String[] inputArray) throws IOException {
        if (inputArray.length != 2) return M_ARGUMENT_MISMATCH;

        outputStream.writeUTF(UserCommands.DOWNLOAD.getCommand() + " " + inputArray[1]);

        String response = readResponse();

        if (!response.equals(ServerResponse.DOWNLOAD_SUCCESS)) {
            return response;
        }

        String fileName = readResponse();
        long fileSize = inputStream.readLong();

        String originalFileName = fileName;
        int c = 0;

        int dot = originalFileName.lastIndexOf('.');

        String name = dot > 0 ? originalFileName.substring(0, dot) : originalFileName;
        String extension = dot > 0 ? originalFileName.substring(dot) : "";

        Path target = resolveDownloadPath(fileName);
        while (Files.exists(target)) {
            ++c;
            fileName = name + "(" + c + ")" + extension;
            target = resolveDownloadPath(fileName);
        }

        System.out.println("Downloading...");



        try (OutputStream fileOut = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = fileSize;

            while (remaining > 0) {
                int bytesToRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = inputStream.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) throw new EOFException("Connection lost");

                fileOut.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }

            return M_DOWNLOAD_SUCCESS;

        } catch (FileNotFoundException e) {
            return M_FNF_OR_DOES_NOT_EXIST;
        }
    }

    private Path resolveDownloadPath(String fileName) throws IOException {
        if (fileName == null
                || fileName.isBlank()
                || fileName.equals(".")
                || fileName.equals("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains(":")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Invalid download file name");
        }

        Path directory = DOWNLOAD_PATH.toAbsolutePath().normalize();
        Path target = directory.resolve(fileName).normalize();

        if (!target.startsWith(directory)) {
            throw new IOException("Download path is outside Downloads");
        }

        return target;
    }

    private void shutdownClient() {
        scanner.close();
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException ignored) {

        } finally {
            System.exit(1);
        }
    }

    static void main(String[] args) {
        Client client = switch (args.length) {
            case 2 -> new Client(args[0], Integer.parseInt(args[1]));
            case 1 -> new Client(args[0]);
            default -> new Client();
        };
        client.printConnectionInfo();

        while (true) {
            client.loginLoop();
            client.commandLoop();
        }
    }
}
