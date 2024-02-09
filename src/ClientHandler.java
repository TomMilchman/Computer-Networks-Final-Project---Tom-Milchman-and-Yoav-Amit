import java.io.*;
import java.net.*;
import java.util.Map;

public class ClientHandler extends Thread {
    private static final String CRLF = "\r\n";

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
                file = new File(filePath + Server.getDefaultPage());
            }

            if (file.exists() && !file.isDirectory()) {
                if (isGetOrPost) {
                    if (httpRequest.isUseChunked()) {
                        sendChunkedFileResponse(getContentType(file), file, clientSocket.getOutputStream());
                    } else {
                        sendFileResponse(getContentType(file), file, out);
                    }
                } else {
                    outputHeaders(200, "OK", getContentType(file), (int) file.length(), out);
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
            String content = httpRequest.getMethod() + " " + httpRequest.getPath() + " HTTP/1.1" + CRLF
                    + "Host: " + clientSocket.getInetAddress().getHostAddress() + CRLF
                    + CRLF;

            sendTextResponse(200, "OK", "message/http", content, out);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(500, "Internal Server Error", out);
        }
    }

    private void handleParamsInfo(HTTPRequest httpRequest, PrintWriter out) {
        try {
            Map<String, String> params = httpRequest.getParameters();

            StringBuilder response = new StringBuilder();
            response.append("<html><body>");
            response.append("<h1>Submitted Parameters:</h1>");
            response.append("<ul>");

            for (Map.Entry<String, String> entry : params.entrySet()) {
                response.append("<li>").append(entry.getKey()).append(": ").append(entry.getValue()).append("</li>");
            }

            response.append("</ul>");
            response.append("</body></html>");

            if (httpRequest.isUseChunked()) {
                sendChunkedTextResponse(200, "OK", "text/html", response.toString(), clientSocket.getOutputStream());
            } else {
                sendTextResponse(200, "OK", "text/html", response.toString(), out);
            }
        } catch (Exception e) {
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

            out.println(headerStr + CRLF + contentTypeStr + CRLF + contentLengthStr + CRLF);
            System.out.println("Sent response: " + headerStr + " " + contentTypeStr + " " + contentLengthStr);
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
        }
    }

    private void sendFileResponse(String contentType, File file, PrintWriter out) throws IOException {
        try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            outputHeaders(200, "OK", contentType, (int) file.length(), out);
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fileStream.read(buffer)) != -1) {
                clientSocket.getOutputStream().write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            sendErrorResponse(400, "Bad Request", out);
        }
    }

    private void sendChunkedFileResponse(String contentType, File file, OutputStream outputStream) throws IOException {
        try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            String headerStr = "HTTP/1.1 " + 200 + " " + "OK";
            String contentTypeStr = "Content-Type: " + contentType;

            // Send the response headers
            String responseHeaders = headerStr + CRLF + "Transfer-Encoding: chunked" + CRLF + contentTypeStr + CRLF + CRLF;
            outputStream.write(responseHeaders.getBytes());
            System.out.println("Sent response: " + headerStr + " Transfer-Encoding: chunked " + contentTypeStr);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fileStream.read(buffer)) != -1) {
                sendChunk(outputStream, buffer, bytesRead);
            }

            sendFinalChunk(outputStream);
        } catch (IOException e) {
            sendErrorResponse(400, "Bad Request", new PrintWriter(outputStream));
        }
    }

    private void sendChunkedTextResponse(int statusCode, String statusMessage, String contentType, String content, OutputStream outputStream) {
        try {
            String headerStr = "HTTP/1.1 " + statusCode + " " + statusMessage;
            String contentTypeStr = "Content-Type: " + contentType;
            String responseHeaders = headerStr + CRLF + "Transfer-Encoding: chunked" + CRLF + contentTypeStr + CRLF + CRLF;
            outputStream.write(responseHeaders.getBytes());

            // Write the content in chunks
            byte[] chunkBuffer = content.getBytes();
            sendChunk(outputStream, chunkBuffer, chunkBuffer.length);
            System.out.println("Sent response: " + headerStr + " Transfer-Encoding: chunked " + contentTypeStr);

            sendFinalChunk(outputStream);
        } catch (IOException e) {
            sendErrorResponse(500, "Internal Server Error", new PrintWriter(new OutputStreamWriter(outputStream)));
        }
    }

    private void sendChunk(OutputStream outputStream, byte[] buffer, int bytesRead) throws IOException {
        // Write the chunk size in hexadecimal followed by CRLF
        String chunkHeader = Integer.toHexString(bytesRead) + CRLF;
        outputStream.write(chunkHeader.getBytes());

        // Write the actual chunk
        outputStream.write(buffer, 0, bytesRead);

        // Write CRLF after each chunk
        outputStream.write(CRLF.getBytes());
    }

    private void sendFinalChunk(OutputStream outputStream) throws IOException {
        // Write the final chunk of size 0 to signal the end
        outputStream.write(("0" + CRLF + CRLF).getBytes());
    }

    private String getContentType(File file) {
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
