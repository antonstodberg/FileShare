package Server;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class ClientHandlerTest {

    @Test
    void unrecognizedCommandReturnsCorrectResponse() throws Exception {
        Server server = new Server(0);

        try (ServerSocket testServer = new ServerSocket(0)) {

            Thread handlerThread = new Thread(() -> {
                try {
                    Socket socket = testServer.accept();

                    ClientHandler handler =
                            new ClientHandler(socket, server);

                    handler.run();

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            handlerThread.start();

            try (Socket client =
                         new Socket("127.0.0.1",
                                 testServer.getLocalPort())) {

                DataOutputStream output =
                        new DataOutputStream(client.getOutputStream());

                DataInputStream input =
                        new DataInputStream(client.getInputStream());

                output.writeUTF("thisIsNotACommand");
                output.flush();

                assertEquals(
                        Protocol.ServerResponse.UNRECOGNIZED_COMMAND,
                        input.readUTF()
                );

                output.writeUTF("exit");
                output.flush();

                assertEquals(
                        Protocol.ServerResponse.EXIT,
                        input.readUTF()
                );
            }

            handlerThread.join(2000);
        }
    }
}