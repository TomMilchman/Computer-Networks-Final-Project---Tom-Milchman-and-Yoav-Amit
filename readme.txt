Overview:
We have implemented a multi-threaded server that allows the client to retrieve files from the server with GET, POST requests, as well as supporting 
HEAD, and TRACE http requests. 
In addition, there is a dynamic html page called params_info.html which outputs the parameters of the request to the client when requested.

Classes: 
Server: This is the main class that is ran. This class first parses the config.ini file and saves the parameters of the file as variables. It then creates
a server socket to which clients are able to connect in order to send requests to the server. The server handles the different requests using a thread pool
which is limited to the maxThreads variable set in the config.ini file. 
ClientHandler: This is a class that extends the Thread class built into Java. This class handles each http request separately. The http request is first
parsed using the HTTPRequest class, after which the request is handled according to the method specified in the request header. In the end, the response
is sent to the client if everything goes well.
HTTPRequest: This class parses an http request and saves the information in its class members. It is also able to parse the parameters in the URL and in the
request body (in the case the method is "POST") and save them in a hashmap for later use.

Design:
We decided to split everything into 3 different class for modularity. The Server class just handles running the server, ClientHandler handles the requests
received from each client and the responses and HTTPRequest is used for easily parsing the requests. In the ClientHandler's run method we determine the type
of request that needs to be handled and handle it accordingly. If the request is of type GET or POST we check first if the file requested is param_info.html,
if it is, we return an HTML page with all of the given parameters inside. If it isn't, we check if the file even exists. If not, we try to obtain the default
page. If the file exists, we return it according to if chunked encoding is requested in the HTTP request or not.