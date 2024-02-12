import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.*;
import java.net.*;

public class Server {
    private static int port;
    private static int maxThreads;
    private static String root;
    private static String defaultPage;

    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        try {
            loadConfig("../config.ini");

            if (port != 0 && maxThreads != 0 && root != null && defaultPage != null) {
                System.out.println("Successfully loaded config.ini: port: "+port+" "+
                "max threads: "+maxThreads+" root: "+root+" default page: "+defaultPage);
            } else {
                throw new Exception("Missing config.ini parameters.");
            }
            
            ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
        
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Server is listening on port " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Thread clientHandler = new ClientHandler(clientSocket);
                    
                    synchronized(threadPool) {
                        // Submit the client handler to the thread pool
                        threadPool.submit(clientHandler);
                    }
                }
            } catch (Exception e) {
                System.out.println("Error encountered running server, shutting down. " + e.getMessage());
            } finally {
                // Shutdown the thread pool when the server is done
                threadPool.shutdown();
            }
        } catch (Exception e) {
            System.out.println("Error encountered running server, shutting down. " + e.getMessage());
        }
    }

    private static void loadConfig(String configFile) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    value = value.replaceFirst("^~", System.getProperty("user.home"));

                    switch (key) {
                        case "port":
                            if (Integer.parseInt(value) > 0 && Integer.parseInt(value) <= 65535) {
                                port = Integer.parseInt(value);
                                break;
                            }

                            throw new Exception("Invalid Port number.");
                        case "root":
                            File file = new File(value);

                            if (file.exists() && file.isDirectory()) {
                                root = value;
                                break;
                            }
                            
                            throw new Exception("Root folder does not exist.");
                        case "defaultPage":
                            defaultPage = value;
                            break;
                        case "maxThreads":
                            if (Integer.parseInt(value) > 0) {
                                maxThreads = Integer.parseInt(value);
                                break;
                            }

                            throw new Exception("Invalid max threads number.");
                    }
                }
            }
        }
    }

    public static String getRoot() {
        return root;
    }

    public static String getDefaultPage() {
        return defaultPage;
    }
}