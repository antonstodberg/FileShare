import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Client {
    private final static String DEFAULT_HOST = "127.0.0.1";
    private final static int DEFAULT_PORT = 2000;

    private Socket socket = new Socket();

    public Client() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public Client(String host) {
        this(host, DEFAULT_PORT);
    }

    public Client(String host, int port) {
        try {
           socket.connect(new InetSocketAddress(host, port), 5000);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public void printConnectionInfo() {
        System.out.println(
                "\n*****************************\n" +
                        "Connection established. \n" +
                        "Host: " + socket.getInetAddress().getHostName() + '\n' +
                        "Port: " + socket.getPort() + '\n' +
                        "*****************************\n"
        );
    }
    public static void main(String[] args) {
        Client client = switch (args.length) {
            case 2 -> new Client(args[0], Integer.parseInt(args[1]));
            case 1 -> new Client(args[0]);
            default -> new Client();
        };
        client.printConnectionInfo();


    }
}
