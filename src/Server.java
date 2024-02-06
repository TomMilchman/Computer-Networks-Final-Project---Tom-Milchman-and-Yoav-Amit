import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.OutputStream;
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
            loadConfig("Computer Networks Final Project - Tom Milchman and Yoav Amit/config.ini");
            System.out.println("Successfully loaded config.ini: port: "+port+" "+
            "max threads: "+maxThreads+" root: "+root+" default page:"+defaultPage);
            
            ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
        
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("Server is listening on port " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    Runnable clientHandler = new ClientHandler(clientSocket);
                    
                    // Submit the client handler to the thread pool and synchronize it
                    synchronized (threadPool) {
                        threadPool.submit(clientHandler);
                    }
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
                        handleGetPostHeadRequest(true, httpRequest, out);
                        break;
                    case "POST":
                        handleGetPostHeadRequest(true, httpRequest, out);
                        break;
                    case "HEAD":
                        handleGetPostHeadRequest(false, httpRequest, out);
                        break;
                    // TODO: Trace
                    default:
                        sendErrorResponse(501, "Not Implemented", out);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private synchronized void handleGetPostHeadRequest(boolean isGetOrPost, HTTPRequest httpRequest, PrintWriter out) throws IOException {
            try {
                String filePath = root + httpRequest.getPath();

                if (!isPathWithinRoot(filePath)) {
                    sendErrorResponse(403, "Forbidden", out);
                    return;
                }

                File file = new File(filePath);
                if (file.exists() && file.getName().equals("params_info.html") && isGetOrPost) {
                    handleParamsInfo(httpRequest, out);
                    return;
                }

                if (file.exists() && file.isDirectory()) {
                    // Request default page
                    file = new File(filePath + "\\" + defaultPage);
                }

                if (file.exists() && !file.isDirectory()) {
                    if (isGetOrPost) {
                        //GET request
                        if (httpRequest.isUseChunked()) {
                            sendChunkedResponse(200, "OK", getContentType(file), file, clientSocket.getOutputStream());
                        } else {
                            sendFileResponse(200, "OK", getContentType(file), file, out);
                        } 
                    } else {
                        //HEAD request
                        outputHeaders(200, "OK", getContentType(file), (int)file.length(), out);
                    }
                } else {
                    sendErrorResponse(404, "Not Found", out);
                }
            } catch (Exception e) {
                e.getStackTrace();
            }
        }

        private void handleParamsInfo(HTTPRequest httpRequest, PrintWriter out) {
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
            sendTextResponse(200, "OK", "text/html", response.toString(), out);
        }

        private void sendErrorResponse(int statusCode, String statusMessage, PrintWriter out) {
            sendTextResponse(statusCode, statusMessage, "text/plain", "", out);
        }

        private void outputHeaders(int statusCode, String statusMessage, String contentType, int contentLength, PrintWriter out) {
            String headerStr = "HTTP/1.1 " + statusCode + " " + statusMessage;
            String contentTypeStr = "Content-Type: " + contentType;
            String contentLengthStr = "Content-Length: " + contentLength;

            out.println(headerStr +"\r\n"+ contentTypeStr +"\r\n"+ contentLengthStr +"\r\n");
            System.out.println("Sent response: " + headerStr +" "+ contentTypeStr +" "+ contentLengthStr);
        }
        
        private void sendTextResponse(int statusCode, String statusMessage, String contentType, String content, PrintWriter out) {
            outputHeaders(statusCode, statusMessage, contentType, content.length(), out);
            out.println(content);
        }

        private void sendFileResponse(int statusCode, String statusMessage, String contentType, File file, PrintWriter out) throws IOException {
            try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
                outputHeaders(statusCode, statusMessage, contentType, (int)file.length(), out);
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    clientSocket.getOutputStream().write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendChunkedResponse(int statusCode, String statusMessage, String contentType, File file, OutputStream outputStream) throws IOException {
            try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
                String headerStr = "HTTP/1.1 " + statusCode + " " + statusMessage;
                String contentTypeStr = "Content-Type: " + contentType;
        
                // Send the response headers
                String responseHeaders = headerStr + "\r\nTransfer-Encoding: chunked\r\n" + contentTypeStr + "\r\n\r\n";
                outputStream.write(responseHeaders.getBytes());
                System.out.println("Sent response: " + headerStr + " Transfer-Encoding: chunked " + contentTypeStr);
        
                byte[] buffer = new byte[1024];
                int bytesRead;
        
                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    // Write the chunk size in hexadecimal followed by CRLF
                    String chunkHeader = Integer.toHexString(bytesRead) + "\r\n";
                    outputStream.write(chunkHeader.getBytes());
        
                    // Write the actual chunk
                    outputStream.write(buffer, 0, bytesRead);
        
                    // Write CRLF after each chunk
                    outputStream.write("\r\n".getBytes());
                }
        
                // Write the final chunk of size 0 to signal the end
                outputStream.write("0\r\n\r\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String getContentType(File file) {
            // Logic to determine content type based on file extension.
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
                case "ico":
                    return "image/x-icon";
                default:
                    return "application/octet-stream";
            }
        }

        private boolean isPathWithinRoot(String filePath) throws IOException {
            //Returns whether requested file is within root or not
            String canonicalFilePath = new File(filePath).getCanonicalPath();
            String canonicalRoot = new File(root).getCanonicalPath();

            return canonicalFilePath.startsWith(canonicalRoot);
        }
    }
}