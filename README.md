                                                    *Project Overview*

This project implements a TCP server-client architecture using the TFTP protocol, with an emphasis on the server side. It features a robust encoder and decoder that adhere to the predefined TFTP conversation rules. The server is designed to efficiently process and respond to client messages, including breaking down responses into packets for transmission. Connection handlers are employed to manage communications effectively.

                                                    *Server Design*

The server operates on the principle of one thread per client, ensuring rapid and maximized availability for each client connection. This design is suitable for handling a moderate number of concurrent clients, ensuring that sufficient resources are available to allocate a dedicated thread for each client. This approach helps in maintaining efficient server performance and quick response times.

                                                *Client Interaction -Example*

Possible command that a client can send to this server is "RRQ" request for a file named "FILENAME". Upon receiving this request, the server quickly checks if the file exists in its library. If the file is found, it converts the file into a byte array and begins transmitting it in packets to the client (in case the file is not exist, it sends back a corresponding error message).
The server also handles various other command types from clients.

-**The client implementation must fit and knows the communiction rules in order to know how to decode the incoming messages and responding back**-

                                             *Enrichment Information - Alternative Models:*
In addition to the one-thread-per-client model , there are more models that can be used in server client communication such that:

1) A single-thread model where one thread handles all client requests, which might be suitable for scenarios with very light loads and minimal concurrency.

2) The reactor model, which maintains a pool of worker threads. This model is designed to efficiently manage a large number of concurrent client connections by delegating requests to available workers from the pool, thus optimizing resource use and scalability.
