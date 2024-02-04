import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStreamReader;

public class Server {
    private static int port;
    private static int maxThreads;
    private static String root;
    private static String defaultPage;

    public static void main(String[] args) throws Exception {
        startServer();
    }

    private static void startServer() {
        try {
            loadConfig("config.ini");
            System.out.println("Successfully loaded config.ini: port: "+port+" "+
            "max threads: "+maxThreads+" root: "+root+" default page:"+defaultPage);
            
            ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
        
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Server is listening on port " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Runnable clientHandler = new ClientHandler(clientSocket);
                    
                    // Submit the client handler to the thread pool
                    threadPool.submit(clientHandler);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Shutdown the thread pool when the server is done
                threadPool.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
           //TODO: Handle server shutdown 
        }
    }

    private static void loadConfig(String configFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    switch (key) {
                        case "port":
                            port = Integer.parseInt(value);
                            break;
                        case "root":
                            root = value;
                            break;
                        case "defaultPage":
                            defaultPage = value;
                            break;
                        case "maxThreads":
                            maxThreads = Integer.parseInt(value);
                            break;
                    }
                }
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String requestLine = in.readLine();
                System.out.println("Received request: " + requestLine);

                HTTPRequest httpRequest = new HTTPRequest(requestLine, in);

                switch (httpRequest.getMethod()) {
                    case "GET":
                        handleGetRequest(httpRequest, out);
                        break;
                    case "POST":
                        handlePostRequest(httpRequest, out, in);
                        break;
                    // Add cases for other HTTP methods if needed
                    // ...
                    // default:
                    //     sendErrorResponse(501, "Not Implemented", out);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleGetRequest(HTTPRequest httpRequest, PrintWriter out) throws IOException {
            int queryIndex = httpRequest.getPath().indexOf('?');
            if (queryIndex != -1) {
                httpRequest.getPath();
            }

            String filePath = root + httpRequest.getPath();

            File file = new File(filePath);

            if (file.exists() && !file.isDirectory()) {
                sendResponse(200, "OK", getContentType(file), file, out);
            } else if (getContentType(file) == "application/octet-stream") {
                //No page is requested, respond with default page
                System.out.println(file.getPath());
                sendResponse(200, "OK", getContentType(file), file, out);
            } else {
                sendErrorResponse(404, "Not Found", out);
            }
        }

        private void handlePostRequest(HTTPRequest httpRequest, PrintWriter out, BufferedReader in) throws IOException {
            // Check if the requested page is /params_info.html
            if ("/params_info.html".equals(httpRequest.getPath())) {
                // Parse parameters from the POST request
                Map<String, String> params = httpRequest.getParameters();

                // Generate the HTML page with details about submitted parameters
                StringBuilder response = new StringBuilder();
                response.append("<html><body>");
                response.append("<h1>Submitted Parameters:</h1>");
                response.append("<ul>");
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    response.append("<li>").append(entry.getKey()).append(": ").append(entry.getValue()).append("</li>");
                }
                response.append("</ul>");
                response.append("</body></html>");

                // Send the HTML response
                sendResponse(200, "OK", "text/html", response.toString(), out);
            } else {
                // Handle other POST requests as needed
                // ...
            }
        }

        private void sendErrorResponse(int statusCode, String statusMessage, PrintWriter out) {
            sendResponse(statusCode, statusMessage, "text/plain", "", out);
        }

        private void sendResponse(int statusCode, String statusMessage, String contentType, String content, PrintWriter out) {
            out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + content.length());
            out.println();
            out.println(content);
        }

        private void sendResponse(int statusCode, String statusMessage, String contentType, File file, PrintWriter out) throws IOException {
            try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
                String headerStr = "HTTP/1.1 " + statusCode + " " + statusMessage;
                String contentTypeStr = "Content-Type: " + contentType;
                String contentLengthStr = "Content-Length: " + file.length();
                
                out.println(headerStr +"\n"+ contentTypeStr +"\n"+ contentLengthStr +"\n");
                System.out.println("Sent response: " + headerStr +" "+ contentTypeStr +" "+ contentLengthStr);
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    clientSocket.getOutputStream().write(buffer, 0, bytesRead);
                }
            }
        }

        private String getContentType(File file) {
            // Implement logic to determine content type based on file extension
            String fileName = file.getName();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

            switch (extension) {
                case "html":
                    return "text/html";
                case "jpg":
                    return "image/jpg";
                case "png":
                    return "image/png";
                case "gif":
                    return "image/gif";
                case "bmp":
                    return "image/bmp";
                default:
                    return "application/octet-stream";
            }
        }
    }
}