import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            System.out.println("Server is listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new ClientHandler(clientSocket);
                clientThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket clientSocket;
        
        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                respondToRequest();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void respondToRequest() throws IOException {
            String httpRes = "HTTP/1.1 200 OK\r\n\r\nYou just connected to the server!";

            try (OutputStream outputStream = clientSocket.getOutputStream();
                 PrintWriter writer = new PrintWriter(outputStream, true)) {
                writer.println(httpRes);
            }
        }
    }
}
