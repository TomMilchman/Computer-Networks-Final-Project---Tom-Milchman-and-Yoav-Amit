import java.util.Map;
import java.io.*;
import java.net.*;

public class ClientHandler extends Thread {
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
                case "POST":
                    handleGetPostHeadRequests(true, httpRequest, out);
                    break;
                case "HEAD":
                    handleGetPostHeadRequests(false, httpRequest, out);
                    break;
                case "TRACE":
                    handleTraceRequest(httpRequest, out);
                    break;
                default:
                    sendErrorResponse(501, "Not Implemented", out);
            }
        } catch (Exception e) {
            System.out.println("Encountered a problem for the request: " + e.getMessage());
        }
    }

    private String cleanPath(String path) {
        // Remove any occurrences of "/../" in the path
        return path.replaceAll("/\\.\\./", "/");
    }

    private synchronized void handleGetPostHeadRequests(boolean isGetOrPost, HTTPRequest httpRequest, PrintWriter out) throws IOException {
        try {
            String cleanPath = cleanPath(httpRequest.getPath());
            String filePath = Server.getRoot() + cleanPath;

            File file = new File(filePath);
            
            if (file.exists() && file.getName().equals("params_info.html") && isGetOrPost) {
                handleParamsInfo(httpRequest, out);
                return;
            }

            if (file.exists() && file.isDirectory()) {
                // Request default page
                file = new File(filePath + "\\" + Server.getDefaultPage());
            }

            if (file.exists() && !file.isDirectory()) {
                if (isGetOrPost) {
                    //GET and POST requests
                    if (httpRequest.isUseChunked()) {
                        sendChunkedResponse(getContentType(file), file, clientSocket.getOutputStream());
                    } else {
                        sendFileResponse(getContentType(file), file, out);
                    } 
                } else {
                    //HEAD request
                    outputHeaders(200, "OK", getContentType(file), (int)file.length(), out);
                }
            } else {
                sendErrorResponse(404, "Not Found", out);
            }
        } catch (Exception e) {
            sendErrorResponse(500, "Internal Server Error", out);
            e.getStackTrace();
        }
    }

    private synchronized void handleTraceRequest(HTTPRequest httpRequest, PrintWriter out) {
        try {
            // Echo back the received request to the client
            String content = httpRequest.getMethod() + " " + httpRequest.getPath() + " HTTP/1.1\r\n"
                            + "Host: " + clientSocket.getInetAddress().getHostAddress() + "\r\n"
                            + "\r\n";
            
            sendTextResponse(200, "OK", "message/http", content, out);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(500, "Internal Server Error", out);
        }
    }

    private void handleParamsInfo(HTTPRequest httpRequest, PrintWriter out) {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(500, "Internal Server Error", out);
        }
    }

    private void sendErrorResponse(int statusCode, String statusMessage, PrintWriter out) {
        sendTextResponse(statusCode, statusMessage, "text/plain", "", out);
    }

    private void outputHeaders(int statusCode, String statusMessage, String contentType, int contentLength, PrintWriter out) {
        try {
            String headerStr = "HTTP/1.1 " + statusCode + " " + statusMessage;
            String contentTypeStr = "Content-Type: " + contentType;
            String contentLengthStr = "Content-Length: " + contentLength;

            out.println(headerStr +"\r\n"+ contentTypeStr +"\r\n"+ contentLengthStr +"\r\n");
            System.out.println("Sent response: " + headerStr +" "+ contentTypeStr +" "+ contentLengthStr);
        } catch (Exception e) {
            sendErrorResponse(400, "Bad Request", out);
        }
    }
    
    private void sendTextResponse(int statusCode, String statusMessage, String contentType, String content, PrintWriter out) {
        try {
            outputHeaders(statusCode, statusMessage, contentType, content.length(), out);
            out.println(content);
        } catch (Exception e) {
            sendErrorResponse(400, "Bad Request", out);
            e.printStackTrace();
        }
    }

    private void sendFileResponse(String contentType, File file, PrintWriter out) throws IOException {
        try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            outputHeaders(200, "OK", contentType, (int)file.length(), out);
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fileStream.read(buffer)) != -1) {
                clientSocket.getOutputStream().write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            sendErrorResponse(400, "Bad Request", out);
            e.printStackTrace();
        }
    }

    private void sendChunkedResponse(String contentType, File file, OutputStream outputStream) throws IOException {
        try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            String headerStr = "HTTP/1.1 " + 200 + " " + "OK";
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
            sendErrorResponse(400, "Bad Request", new PrintWriter(outputStream));
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
}
