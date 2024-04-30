package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

import bgu.spl.net.srv.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TftpServer<T> {

    // start server:
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> listOfUsersByName = new ConcurrentHashMap<String, Integer>();
        ConcurrentHashMap<Integer, byte[]> idToFile = new ConcurrentHashMap<Integer, byte[]>();
        ConcurrentHashMap<Integer, Integer> idToCurrExpectAck = new ConcurrentHashMap<Integer, Integer>();
        ConcurrentHashMap<Integer, Integer> idToNumOfBlocks = new ConcurrentHashMap<Integer, Integer>();
        ConcurrentHashMap<String, Boolean> activeUsers = new ConcurrentHashMap<String, Boolean>();// maps from name
                                                                                                  // to
                                                                                                  // boolean(active
                                                                                                  // or not)
        ConcurrentHashMap<Integer, String> listOfUsersById = new ConcurrentHashMap<Integer, String>();// maps from
                                                                                                      // Id ti Name

        ConcurrentHashMap<Integer, byte[]> idToFileUpload = new ConcurrentHashMap<Integer, byte[]>();
        ConcurrentHashMap<Integer, String> idToFileUploadName = new ConcurrentHashMap<Integer, String>();
        ConcurrentHashMap<Integer, Boolean> activeUsersById = new ConcurrentHashMap<Integer, Boolean>();
        // you can use any server...
        Server.threadPerClient(
                7777, // port
                () -> new TftpProtocol(idToFile, idToCurrExpectAck, idToNumOfBlocks, listOfUsersByName, activeUsers,
                        listOfUsersById, idToFileUpload, idToFileUploadName, activeUsersById), // protocol factory
                TftpEncoderDecoder::new // message encoder decoder factory
        ).serve();

    }
}

// TODO: Implement this
