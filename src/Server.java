import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.io.InputStreamReader;

public class Server {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT)){
            System.out.println("Server is listening on port " + DEFAULT_PORT);

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
                    // case "HEAD":
                    //     handleHeadRequest(httpRequest, out);
                    //     break;
                    // case "TRACE":
                    //     handleTraceRequest(httpRequest, out, in);
                    //     break;
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

            // if (httpRequest.getPath().equals("/favicon.ico")) {
            //     sendFaviconResponse(httpRequest, out);
            //     return;
            // }

            String filePath = "C:\\Users\\tommi\\Documents\\University\\Y3S1\\Networks\\Final Project\\Rootdir" + httpRequest.getPath();

            // if (!isPathWithinRoot(filePath)) {
            //     sendErrorResponse(403, "Forbidden", out);
            //     return;
            // }

            File file = new File(filePath);

            if (file.exists() && !file.isDirectory()) {
                sendOkResponse(httpRequest, out, file);
            } else {
                sendErrorResponse(404, "Not Found", out);
            }
        }

        private void sendOkResponse(HTTPRequest httpRequest, PrintWriter out, File file) throws IOException {
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Length: " + file.length());
            out.println("Content-Type: " + getContentType(file));
            out.println();
            sendFileContent(out, file);
        }

        // private boolean shouldUseChunkedEncoding(HTTPRequest httpRequest) {
        //     String chunkedHeader = httpRequest.getHeaders().get("chunked");
        //     return "yes".equalsIgnoreCase(chunkedHeader);
        // }

        private void sendErrorResponse(int statusCode, String statusMessage, PrintWriter out) {
            out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            out.println();
        }

        private boolean isPathWithinRoot(String filePath) throws IOException {
            String canonicalFilePath = new File(filePath).getCanonicalPath();
            String canonicalRoot = new File("C:\\Users\\tommi\\Documents\\University\\Y3S1\\Networks\\Final Project\\Rootdir").getCanonicalPath();
            return canonicalFilePath.startsWith(canonicalRoot);
        }

        private String getContentType(File file) {
            // Implement logic to determine content type based on file extension
            // For simplicity, this example assumes everything is treated as text/html
            return "text/html";
        }

        private void sendFileContent(PrintWriter out, File file) throws IOException {
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                char[] buffer = new char[1024];
                int bytesRead;
                while ((bytesRead = fileReader.read(buffer)) != -1) {
                    out.print(buffer);
                }
            }
        }

        private void sendFileContentChunked(PrintWriter out, File file) throws IOException {
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                char[] buffer = new char[1024];
                int bytesRead;
                while ((bytesRead = fileReader.read(buffer)) != -1) {
                    out.println(Integer.toHexString(bytesRead));
                    out.print(buffer);
                    out.println();
                }
                out.println("0");
                out.println();
            }
        }

        private void handlePostRequest(HTTPRequest httpRequest, PrintWriter out, BufferedReader in) throws IOException {
            // Check if the requested page is /params_info.html
            if ("/params_info.html".equals(httpRequest.getPath())) {
                // Parse parameters from the POST request
                java.util.Map<String, String> params = httpRequest.getParameters();
        
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

        private void sendResponse(int statusCode, String statusMessage, String contentType, String content, PrintWriter out) {
            // Send a complete HTTP response
            sendResponseHeaders(statusCode, statusMessage, contentType, content, out);
            out.println(content);
        }
    
        private void sendResponseHeaders(int statusCode, String statusMessage, String contentType, String content, PrintWriter out) {
            // Send HTTP response headers
            out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
            out.println("content-type: " + contentType);
            out.println("content-length: " + content.length());
            out.println("");
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
