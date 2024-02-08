import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HTTPRequest {
    private String method;
    private String path;
    private boolean isImage;
    private int contentLength;
    private String referer;
    private String userAgent;
    private boolean useChunked;
    private Map<String, String> parameters;

    public HTTPRequest(String requestLine, BufferedReader in) throws IOException {
        parameters = new HashMap<>();
        parseRequestLine(requestLine);
        parseHeaders(in);
    }

    private void parseRequestLine(String requestLine) {
        String[] requestParts = requestLine.split(" ");

        if (requestParts.length == 3) {
            method = requestParts[0];
            String fullPath = requestParts[1];

            int queryIndex = fullPath.indexOf('?');
            
            if (queryIndex != -1) {
                path = fullPath.substring(0, queryIndex);
                String queryString = fullPath.substring(queryIndex + 1);
                parseParameters(queryString);
            } else {
                path = fullPath;
            }

            isImage = isPathImage(path);
        }
    }

    private void parseHeaders(BufferedReader in) throws IOException {
        String line;
        
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
            } else if (line.startsWith("Referer:")) {
                referer = line.substring("Referer:".length()).trim();
            } else if (line.startsWith("User-Agent:")) {
                userAgent = line.substring("User-Agent:".length()).trim();
            } else if (line.startsWith("Transfer-Encoding: chunked")) {
                useChunked = true;
            }
        }

        // POST request's content body parameters
        if ("POST".equals(method) && contentLength > 0) {
            StringBuilder requestBody = new StringBuilder();
            char[] buffer = new char[contentLength];
            in.read(buffer, 0, contentLength);
            requestBody.append(buffer);
            parseParameters(requestBody.toString());
        }
    }

    private boolean isPathImage(String path) {
        String[] imageExtensions = {".jpg", ".bmp", ".gif", ".png"};

        for (String extension : imageExtensions) {
            if (path.toLowerCase().endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    private void parseParameters(String requestBody) {
        String[] paramPairs = requestBody.split("&");
    
        for (String pair : paramPairs) {
            String[] keyValue = pair.split("=");

            if (keyValue.length == 2) {
                try {
                    String decodedKey = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String decodedValue = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    parameters.put(decodedKey, decodedValue);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public boolean isImage() {
        return isImage;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getReferer() {
        return referer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public boolean isUseChunked() {
        return useChunked;
    }
}
