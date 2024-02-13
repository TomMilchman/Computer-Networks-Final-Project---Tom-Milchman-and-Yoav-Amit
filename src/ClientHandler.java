import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ClientHandler extends Thread {
    private static final String CRLF = "\r\n";
    private static final ReentrantLock lock = new ReentrantLock();

    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String requestLine = in.readLine();
            HTTPRequest httpRequest = new HTTPRequest(requestLine, in);
            printRequestHeaders(httpRequest, requestLine);

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

    private void printRequestHeaders(HTTPRequest httpRequest, String requestLine) {
        lock.lock();

        System.out.println("--------------------------------------------------");
        System.out.println("Received request:");
        System.out.println(requestLine);
        System.out.println(httpRequest.headersAsString());
        System.out.println("--------------------------------------------------");
        
        lock.unlock();
    }

    private void handleGetPostHeadRequests(boolean isGetOrPost, HTTPRequest httpRequest, PrintWriter out) throws IOException {
        try {
            String filePath = Server.getRoot() + httpRequest.getPath();

            File file = new File(filePath);

            if (file.exists() && file.getName().equals("params_info.html") && isGetOrPost) {
                //Return the inserted parameters to the user in an html page 
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
                        sendChunkedFileResponse(getContentType(file), file, out);
                    } else {
                        sendFileResponse(file, getContentType(file), out);
                    }
                } else {
                    outputResponseHeaders(200, "OK", getContentType(file), (int) file.length(), out);
                }
            } else {
                sendErrorResponse(404, "Not Found", out);
            }
        } catch (Exception e) {
            sendErrorResponse(500, "Internal Server Error", out);
            e.printStackTrace();
        }
    }

    private void handleTraceRequest(HTTPRequest httpRequest, PrintWriter out) {
        try {
            String requestMessage = httpRequest.getMethod() + " " + httpRequest.getPath() + " HTTP/1.1" + CRLF
                    + httpRequest.headersAsString();
            
            if (httpRequest.isUseChunked()) {
                sendChunkedTextResponse("message/http", requestMessage, out);
            } else {
                sendTextResponse(200, "OK", "message/http", requestMessage, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(500, "Internal Server Error", out);
        }
    }

    private void handleParamsInfo(HTTPRequest httpRequest, PrintWriter out) {
        try {
            //Create and send an html with all the user inserted parameters.
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
                sendChunkedTextResponse("text/html", response.toString(), out);
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

    private void outputResponseHeaders(int statusCode, String statusMessage, String contentType, int contentLength, PrintWriter out) {
        lock.lock();
        
        try {
            String requestLineStr = "HTTP/1.1 " + statusCode + " " + statusMessage;
            String contentTypeStr = "Content-Type: " + contentType;
            String contentLengthStr = "Content-Length: " + contentLength;
            String response = requestLineStr + CRLF + contentTypeStr + CRLF + contentLengthStr;

            out.println(response + CRLF);
            System.out.println("--------------------------------------------------");
            System.out.println("Sent response: " + CRLF + response);
            System.out.println("--------------------------------------------------");
        } catch (Exception e) {
            sendErrorResponse(400, "Bad Request", out);
        } finally {
            lock.unlock();
        }
    }

    private void sendTextResponse(int statusCode, String statusMessage, String contentType, String content, PrintWriter out) {
        try {
            outputResponseHeaders(statusCode, statusMessage, contentType, content.length(), out);
            out.println(content);
        } catch (Exception e) {
            sendErrorResponse(400, "Bad Request", out);
        }
    }

    private void sendFileResponse(File file, String contentType, PrintWriter out) {
        try {
            byte[] fileContent = readFile(file);
            OutputStream outputStream = clientSocket.getOutputStream();
    
            if (fileContent != null) {
                outputResponseHeaders(200, "OK", contentType, fileContent.length, out);
                outputStream.write(fileContent);
            } else {
                sendErrorResponse(400, "Bad Request", out);
            }
        } catch (Exception e) {
            sendErrorResponse(500, "Internal Server Error", out);
        }
    }
    

    private byte[] readFile(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bFile = new byte[(int) file.length()];

            // Read until the end of the stream.
            while (fis.available() != 0) {
                fis.read(bFile, 0, bFile.length);
            }

            return bFile;
        } 
    }

    private void sendChunkedFileResponse(String contentType, File file, PrintWriter out) throws IOException {
        try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            OutputStream outputStream = clientSocket.getOutputStream();
            String requestLineStr = "HTTP/1.1 " + 200 + " " + "OK";
            String contentTypeStr = "Content-Type: " + contentType;

            // Send the response headers
            String responseHeaders = requestLineStr + CRLF + "Transfer-Encoding: chunked" + CRLF + contentTypeStr + CRLF + CRLF;
            outputStream.write(responseHeaders.getBytes());
            printChunkedResponseToConsole(requestLineStr, contentTypeStr);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fileStream.read(buffer)) != -1) {
                sendChunk(outputStream, buffer, bytesRead);
            }

            sendFinalChunk(outputStream);
        } catch (IOException e) {
            sendErrorResponse(400, "Bad Request", out);
        }
    }

    private void sendChunkedTextResponse(String contentType, String content, PrintWriter out) {
        try {
            OutputStream outputStream = clientSocket.getOutputStream();
            String requestLineStr = "HTTP/1.1 " + 200 + " " + "OK";
            String contentTypeStr = "Content-Type: " + contentType;
            String responseHeaders = requestLineStr + CRLF + "Transfer-Encoding: chunked" + CRLF + contentTypeStr + CRLF + CRLF;
            outputStream.write(responseHeaders.getBytes());

            // Write the content in chunks
            byte[] chunkBuffer = content.getBytes();
            sendChunk(outputStream, chunkBuffer, chunkBuffer.length);
            printChunkedResponseToConsole(requestLineStr, contentTypeStr);

            sendFinalChunk(outputStream);
        } catch (IOException e) {
            sendErrorResponse(400, "Bad Request", out);
        }
    }

    private void printChunkedResponseToConsole(String requestLineStr, String contentTypeStr) {
        lock.lock();
        
        System.out.println("--------------------------------------------------");
        System.out.println("Sent response: " + CRLF + requestLineStr + CRLF + "Transfer-Encoding: chunked " + CRLF + contentTypeStr);
        System.out.println("--------------------------------------------------");

        lock.unlock();
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
            case "png":
            case "gif":
            case "bmp":
                return "image";
            case "ico":
                return "icon";
            default:
                return "application/octet-stream";
        }
    }
}
