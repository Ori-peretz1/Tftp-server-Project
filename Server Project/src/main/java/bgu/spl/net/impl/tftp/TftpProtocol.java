package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsIm;

import java.util.concurrent.ConcurrentHashMap;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connectionsim;
    private String userName;
    int waitForData = 0; // indicate if we are waiting for data , 0 or 1.

    private ConcurrentHashMap<Integer, byte[]> idToFile;// maps between
                                                        // client to file he
                                                        // uploading
                                                        // currently
    private ConcurrentHashMap<Integer, Integer> idToCurrExpectAck;
    private ConcurrentHashMap<Integer, Integer> idToNumOfBlocks;

    private ConcurrentHashMap<String, Integer> listOfUsersByName; // maps from
                                                                  // name to
                                                                  // id
    private ConcurrentHashMap<String, Boolean> activeUsers;// maps from name
                                                           // to
                                                           // boolean(active
                                                           // or not)
    private ConcurrentHashMap<Integer, String> listOfUsersById;// maps from
                                                               // Id ti Name

    private ConcurrentHashMap<Integer, byte[]> idToFileUpload;
    private ConcurrentHashMap<Integer, String> idToFileUploadName;
    private ConcurrentHashMap<Integer, Boolean> activeUsersById;// maps between uniqe Id to activness

    // CONSTRUCTOR ::::::!!!!!!_______________________________________
    public TftpProtocol(ConcurrentHashMap<Integer, byte[]> idToFile,
            ConcurrentHashMap<Integer, Integer> idToCurrExpectAck, ConcurrentHashMap<Integer, Integer> idToNumOfBlocks,
            ConcurrentHashMap<String, Integer> listOfUsersByName, ConcurrentHashMap<String, Boolean> activeUsers,
            ConcurrentHashMap<Integer, String> listOfUsersById,
            ConcurrentHashMap<Integer, byte[]> idToFileUpload, ConcurrentHashMap<Integer, String> idToFileUploadName,
            ConcurrentHashMap<Integer, Boolean> activeUsersById) {
        this.idToFile = idToFile;
        this.idToCurrExpectAck = idToCurrExpectAck;
        this.idToNumOfBlocks = idToNumOfBlocks;
        this.listOfUsersByName = listOfUsersByName;
        this.activeUsers = activeUsers;
        this.listOfUsersById = listOfUsersById;
        this.idToFileUpload = idToFileUpload;
        this.idToFileUploadName = idToFileUploadName;
        this.activeUsersById = activeUsersById;

    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.connectionId = connectionId;
        this.connectionsim = connections;

    }

    @Override
    public void process(byte[] message) {
        // TODO implement this

        short opCode = (short) ((short) message[0] << 8 | (short) message[1] & 0x00FF);

        switch (opCode) {
            case 7: // login -LOGRQ
                byte[] userName = new byte[message.length - 2];
                System.arraycopy(message, 2, userName, 0, userName.length);// cut the name part from the packet
                String name = new String(userName, StandardCharsets.UTF_8);
                // user already logged
                if (activeUsersById.containsKey(connectionId)) {
                    if (activeUsersById.get(connectionId)) {// user already logged
                        String errorMsg = "User already logged in - Login username already connected.";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);
                        byte[] errorBack = new byte[5 + errorMsgB.length];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 7;// the first four bytes-op and code error
                        errorBack[errorBack.length - 1] = 0;// the final byte=0
                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        connectionsim.send(connectionId, errorBack);
                    } else {// logged in once
                        listOfUsersByName.put(name, connectionId);
                        listOfUsersById.put(connectionId, name);
                        activeUsers.put(name, true);
                        activeUsersById.put(connectionId, true);
                        // sending ack 0
                        byte[] ack0 = { 0, 4, 0, 0 };
                        // initiallize the blockNum and expected Ack.
                        idToCurrExpectAck.put(connectionId, 0);
                        idToNumOfBlocks.put(connectionId, 0);
                        connectionsim.send(connectionId, ack0);
                    }

                } else {// never logged in
                    if (listOfUsersByName.containsKey(name)) {// checking there is no name like this
                        String errorMsg = "User already logged in - Login username already connected.";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                        byte[] errorBack = new byte[5 + errorMsgB.length];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 7;// the first four bytes-op and code error
                        errorBack[errorBack.length - 1] = 0;// the final byte=0
                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        connectionsim.send(connectionId, errorBack);
                    } else {
                        listOfUsersByName.put(name, connectionId);
                        listOfUsersById.put(connectionId, name);
                        activeUsers.put(name, true);
                        activeUsersById.put(connectionId, true);
                        // sending ack 0
                        byte[] ack0 = { 0, 4, 0, 0 };
                        // initiallize the blockNum and expected Ack.
                        idToCurrExpectAck.put(connectionId, 0);
                        idToNumOfBlocks.put(connectionId, 0);

                        connectionsim.send(connectionId, ack0);

                    }

                }
                break;

            case 10: // disconnect - DISC

                if (!activeUsersById.containsKey(connectionId)) {// user didnt logged once
                    // return error because he isnt logged no 6
                    String errorMsg = "User not logged in - Any opcode received before Login completes.";
                    byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);
                    byte[] errorBack = new byte[5 + errorMsgB.length];
                    errorBack[0] = 0;
                    errorBack[1] = 5;
                    errorBack[2] = 0;
                    errorBack[3] = 6;// the first four bytes-op and code error
                    errorBack[errorBack.length - 1] = 0;// the final byte=0
                    for (int i = 4; i < errorBack.length - 1; i++) {
                        errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                    }
                    connectionsim.send(connectionId, errorBack);
                    break;

                } else {
                    if (activeUsersById.get(connectionId)) {
                        // return error because he isnt logged no 6
                        String errorMsg = "User not logged in - Any opcode received before Login completes.";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);
                        byte[] errorBack = new byte[5 + errorMsgB.length];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 6;// the first four bytes-op and code error
                        errorBack[errorBack.length - 1] = 0;// the final byte=0
                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        connectionsim.send(connectionId, errorBack);

                    } else {
                        String nameToDisc = listOfUsersById.get(connectionId);

                        if (activeUsers.get(nameToDisc)) {
                            activeUsers.remove(nameToDisc); // remove first to make sure it wont add one more client
                                                            // with
                                                            // the same features
                            activeUsers.put(nameToDisc, false);
                            activeUsersById.remove(connectionId);
                            activeUsersById.put(connectionId, false);
                            // sending packet ack 0
                            byte[] ack0 = { 0, 4, 0, 0 };
                            connectionsim.send(connectionId, ack0);

                        }
                    }
                }
                break;


            case 1:// RRQ
                if (activeUsersById.containsKey(connectionId) && activeUsersById.get(connectionId)) {
                    byte[] fileNameBytes = Arrays.copyOfRange(message, 2, message.length);// dosnt include the end
                    String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                    Path filePath = Paths.get("server" + "/" + "Files" + "/" + fileName);
                    boolean isExist = Files.exists(filePath);


                    if (!isExist) {// there is no file like this
                        String errorMsg = "File not found - RRQ DELRQ of non-existing file.";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                        byte[] errorBack = new byte[5 + errorMsgB.length];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 1;// the first four bytes-op and code error
                        errorBack[errorBack.length - 1] = 0;// the final byte=0
                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        connectionsim.send((connectionId), errorBack);
                        break;
                    } else {
                        byte[] requestedFile = new byte[0];
                        try {
                            requestedFile = Files.readAllBytes(filePath);
                            idToFile.put(connectionId, requestedFile);// adding to hashmap of files
                        } catch (IOException e) {
                            String errorMsg = "Access violation - File cannot be written, read or deleted.";
                            byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                            byte[] errorBack = new byte[5 + errorMsgB.length];
                            errorBack[0] = 0;
                            errorBack[1] = 5;
                            errorBack[2] = 0;
                            errorBack[3] = 2;// the first four bytes-op and code error
                            errorBack[errorBack.length - 1] = 0;// the final byte=0

                            for (int i = 4; i < errorBack.length - 1; i++) {
                                errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                            }
                            connectionsim.send(connectionId, errorBack);// sending the error
                            break;
                        }
                        int numOfPackets = requestedFile.length / 512; // num of full packets

                        if ((requestedFile.length) % 512 >= 0) {
                            numOfPackets++; // adding one for the last that has less than 512
                        }

                        idToNumOfBlocks.remove(connectionId);
                        idToNumOfBlocks.put(connectionId, numOfPackets); // update num of expected ack blocks
                        idToCurrExpectAck.remove(connectionId);
                        idToCurrExpectAck.put(connectionId, 1);

                        if (numOfPackets > 1) {
                            byte[] packetSize = new byte[2];
                            packetSize[0] = (byte) ((512 >> 8));
                            packetSize[1] = (byte) (512 & 0x00FF); // casting 512 to bytes for all of the full packets
                            byte[] firstPack = new byte[518];

                            System.arraycopy(requestedFile, 0, firstPack, 6, 512);
                            firstPack[0] = 0;
                            firstPack[1] = 3;
                            firstPack[2] = packetSize[0];
                            firstPack[3] = packetSize[1];
                            firstPack[4] = 0;
                            firstPack[5] = 1;

                            connectionsim.send(connectionId, firstPack);
                        } else { // case the data is less than 512 and can send at one time
                            byte[] firstPack = new byte[6 + requestedFile.length];
                            System.arraycopy(requestedFile, 0, firstPack, 6, requestedFile.length);
                            firstPack[0] = 0;
                            firstPack[1] = 3;
                            byte[] packetSize = new byte[2];
                            packetSize[0] = (byte) ((requestedFile.length >> 8));
                            packetSize[1] = (byte) (requestedFile.length & 0x00FF);
                            firstPack[2] = packetSize[0];
                            firstPack[3] = packetSize[1];
                            firstPack[4] = 0;
                            firstPack[5] = 1;
                            connectionsim.send(connectionId, firstPack);
                        }
                    }
                } else {// the user no active
                    String errorMsg = "User not logged in - Any opcode received before Login completes.";
                    byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                    byte[] errorBack = new byte[5 + errorMsgB.length];
                    errorBack[0] = 0;
                    errorBack[1] = 5;
                    errorBack[2] = 0;
                    errorBack[3] = 6;// the first four bytes-op and code error
                    errorBack[errorBack.length - 1] = 0;// the final byte=0
                    for (int i = 4; i < errorBack.length - 1; i++) {
                        errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                    }
                    connectionsim.send(connectionId, errorBack);
                }
                break;

            case 2:// WRQ
                if (activeUsersById.containsKey(connectionId) && activeUsersById.get(connectionId)) {
                    byte[] fileNametoUp = Arrays.copyOfRange(message, 2, message.length);
                    String fileNameUp = new String(fileNametoUp, StandardCharsets.UTF_8);

                    Path filePathW = Paths.get("server" + "/" + "Files" + "/" + fileNameUp);

                    boolean isExistw = Files.exists(filePathW);
                    if (isExistw) {
                        String errorMsg = "File already exists - File name exists on WRQ.";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                        byte[] errorBack = new byte[5 + errorMsgB.length];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 5;// the first four bytes-op and code error
                        errorBack[errorBack.length - 1] = 0;// the final byte=0
                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        connectionsim.send((connectionId), errorBack);

                    } else {// its ok to upload the file
                        byte[] ack0 = { 0, 4, 0, 0 };
                        waitForData = 1;// update the flag for waiting for data
                        // File uploadFile = new File("files"+ "\\"+ fileNameUp );

                        idToFileUploadName.put(connectionId, fileNameUp);
                        byte[] fileE = new byte[0];
                        idToFileUpload.put(connectionId, fileE);
                        connectionsim.send((connectionId), ack0);

                    }

                } else {// user tried to upload file without signed in
                    String errorMsg = "User not logged in - Any opcode received before Login completes.";
                    byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                    byte[] errorBack = new byte[5 + errorMsgB.length];
                    errorBack[0] = 0;
                    errorBack[1] = 5;
                    errorBack[2] = 0;
                    errorBack[3] = 6;// the first four bytes-op and code error
                    errorBack[errorBack.length - 1] = 0;// the final byte=0
                    for (int i = 4; i < errorBack.length - 1; i++) {
                        errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                    }
                    connectionsim.send(connectionId, errorBack);
                }

                break;

            case 4: // ACK
                short blockNo = (short) ((short) message[2] << 8 | (short) message[3] & 0x00FF);

                if (blockNo < idToNumOfBlocks.get(connectionId)) {

                    blockNo++;
                    int sizeLeft = idToFile.get(connectionId).length - (blockNo - 1) * 512;

                    if ((blockNo - 1) == idToCurrExpectAck.get(connectionId)) {
                        if (sizeLeft == 0) {// case the size divided by 512
                            byte[] lastD = new byte[6];
                            lastD[0] = 0;
                            lastD[1] = 3;
                            lastD[2] = 0;
                            lastD[3] = 0;
                            short i = (short) (idToCurrExpectAck.get(connectionId) + 1);

                            lastD[4] = (byte) ((i >> 8));

                            lastD[5] = (byte) (i & 0X00FF);
                            idToCurrExpectAck.remove(connectionId);
                            idToCurrExpectAck.put(connectionId, 0);
                            idToNumOfBlocks.remove(connectionId);
                            idToNumOfBlocks.put(connectionId, 0);
                            connectionsim.send(connectionId, lastD);
                            break;

                        }

                        if (sizeLeft > 512) {
                            byte[] nextPack = new byte[518];
                            byte[] packetSize = new byte[2];
                            packetSize[0] = (byte) ((512 >> 8));
                            packetSize[1] = (byte) (512 & 0x00FF); // casting 512 to bytes for full packets
                            System.arraycopy(idToFile.get(connectionId), idToCurrExpectAck.get(connectionId) * 512,
                                    nextPack, 6, 512);// making sure only the right block will cut from the big bytes
                                                      // array
                                                      // for the next packet
                            nextPack[0] = 0;
                            nextPack[1] = 3;
                            nextPack[2] = packetSize[0];
                            nextPack[3] = packetSize[1];
                            short i = (short) (idToCurrExpectAck.get(connectionId) + 1);

                            nextPack[4] = (byte) ((i >> 8));

                            nextPack[5] = (byte) (i & 0X00FF);
                            int nextExpected = idToCurrExpectAck.get(connectionId);
                            idToCurrExpectAck.remove(connectionId);
                            nextExpected++;
                            idToCurrExpectAck.put(connectionId, nextExpected);
                            connectionsim.send(connectionId, nextPack);
                        } else {
                            if (sizeLeft == 512) {
                                byte[] nextPack = new byte[6 + sizeLeft];
                                byte[] packetSize = new byte[2];
                                packetSize[0] = (byte) ((sizeLeft >> 8));
                                packetSize[1] = (byte) (sizeLeft & 0x00FF);
                                System.arraycopy(idToFile.get(connectionId), idToCurrExpectAck.get(connectionId) * 512,
                                        nextPack, 6, sizeLeft);// making sure only the right block will cut from the big
                                                               // bytes
                                                               // array for the last packet
                                short i = (short) (idToCurrExpectAck.get(connectionId) + 1);

                                nextPack[0] = 0;
                                nextPack[1] = 3;
                                nextPack[2] = packetSize[0];
                                nextPack[3] = packetSize[1];
                                nextPack[4] = (byte) ((i >> 8));
                                nextPack[5] = (byte) (i & 0X00FF);
                                idToCurrExpectAck.put(connectionId, idToCurrExpectAck.get(connectionId) + 1);
                                connectionsim.send(connectionId, nextPack);
                                break;

                            } else {
                                byte[] nextPack = new byte[6 + sizeLeft];
                                byte[] packetSize = new byte[2];
                                packetSize[0] = (byte) ((sizeLeft >> 8));
                                packetSize[1] = (byte) (sizeLeft & 0x00FF);
                                System.arraycopy(idToFile.get(connectionId), idToCurrExpectAck.get(connectionId) * 512,
                                        nextPack, 6, sizeLeft);// making sure only the right block will cut from the big
                                                               // bytes
                                                               // array for the last packet
                                short i = (short) (idToCurrExpectAck.get(connectionId) + 1);

                                nextPack[0] = 0;
                                nextPack[1] = 3;
                                nextPack[2] = packetSize[0];
                                nextPack[3] = packetSize[1];
                                nextPack[4] = (byte) ((i >> 8));
                                nextPack[5] = (byte) (i & 0X00FF);

                                idToCurrExpectAck.remove(connectionId);
                                idToCurrExpectAck.put(connectionId, 0);
                                idToNumOfBlocks.remove(connectionId);
                                idToNumOfBlocks.put(connectionId, 0);
                                connectionsim.send(connectionId, nextPack);
                            }

                        }
                    } else {
                        String errorMsg = "not compitable ack blockNum!";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                        byte[] errorBack = new byte[5 + errorMsg.length()];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 0;//
                        errorBack[errorBack.length - 1] = 0;// the final byte=0

                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        idToCurrExpectAck.remove(connectionId);
                        idToCurrExpectAck.put(connectionId, 0);
                        idToNumOfBlocks.remove(connectionId);
                        idToNumOfBlocks.put(connectionId, 0);

                        connectionsim.send(connectionId, errorBack);// sending the error
                    }
                }

                break;

            case 3: // DATA
                if (waitForData == 0) {
                    String errorMsg = "Access violation - File cannot be written, read or deleted.";
                    byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                    byte[] errorBack = new byte[5 + errorMsg.length()];
                    errorBack[0] = 0;
                    errorBack[1] = 5;
                    errorBack[2] = 0;
                    errorBack[3] = 2;//
                    errorBack[errorBack.length - 1] = 0;// the final byte=0

                    for (int i = 4; i < errorBack.length - 1; i++) {
                        errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                    }
                    idToCurrExpectAck.remove(connectionId);
                    idToCurrExpectAck.put(connectionId, 0);
                    idToNumOfBlocks.remove(connectionId);
                    idToNumOfBlocks.put(connectionId, 0);

                    connectionsim.send(connectionId, errorBack);// sending the error

                } else {

                    short sizeOfPack = (short) ((short) message[2] << 8 | (short) message[3] & 0x00FF);
                    short blockN = (short) ((short) message[4] << 8 | (short) message[5] & 0x00FF);

                    if (sizeOfPack >= 512) {

                        byte[] curr = idToFileUpload.get(connectionId);
                        byte[] updated = new byte[curr.length + 512];
                        System.arraycopy(curr, 0, updated, 0, curr.length);
                        System.arraycopy(message, 6, updated, curr.length, 512);
                        idToFileUpload.put(connectionId, updated);
                        byte[] ackBlo = new byte[4];
                        ackBlo[0] = 0;
                        ackBlo[1] = 4;
                        ackBlo[2] = (byte) ((blockN >> 8));
                        ackBlo[3] = (byte) (blockN & 0x00FF);
                        connectionsim.send(connectionId, ackBlo);

                    } else {// this is the last packet (maybe only one )
                        byte[] curr = idToFileUpload.get(connectionId);

                        byte[] updated = new byte[curr.length + message.length - 6];

                        if (curr.length == 0) {
                            updated = Arrays.copyOfRange(message, 6, message.length);
                        } else {
                            System.arraycopy(curr, 0, updated, 0, curr.length);
                            System.arraycopy(message, 6, updated, curr.length, sizeOfPack);
                        }

                        idToFileUpload.put(connectionId, updated);
                        String fileNa = idToFileUploadName.get(connectionId);
                        File uploadFile = new File("server" + "/" + "Files" + "/" + fileNa);
                        try {
                            uploadFile.createNewFile();
                        } catch (IOException e) {
                            String errorMsg = " Access violation - File cannot be written, read or deleted.";
                            byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                            byte[] errorBack = new byte[5 + errorMsg.length()];
                            errorBack[0] = 0;
                            errorBack[1] = 5;
                            errorBack[2] = 0;
                            errorBack[3] = 2;//
                            errorBack[errorBack.length - 1] = 0;// the final byte=0

                            for (int i = 4; i < errorBack.length - 1; i++) {
                                errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                            }
                            connectionsim.send(connectionId, errorBack);
                            break;

                        }
                        FileOutputStream uploadedFile = null;
                        try {
                            uploadedFile = new FileOutputStream(uploadFile);
                        } catch (FileNotFoundException e) {
                            String errorMsg = " Access violation - File cannot be written, read or deleted.";
                            byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                            byte[] errorBack = new byte[5 + errorMsg.length()];
                            errorBack[0] = 0;
                            errorBack[1] = 5;
                            errorBack[2] = 0;
                            errorBack[3] = 2;//
                            errorBack[errorBack.length - 1] = 0;// the final byte=0

                            for (int i = 4; i < errorBack.length - 1; i++) {
                                errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                            }
                            connectionsim.send(connectionId, errorBack);
                            break;

                        }
                        try {
                            uploadedFile.write(updated);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            String errorMsg = " Access violation - File cannot be written, read or deleted.";
                            byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                            byte[] errorBack = new byte[5 + errorMsg.length()];
                            errorBack[0] = 0;
                            errorBack[1] = 5;
                            errorBack[2] = 0;
                            errorBack[3] = 2;//
                            errorBack[errorBack.length - 1] = 0;// the final byte=0

                            for (int i = 4; i < errorBack.length - 1; i++) {
                                errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                            }
                            connectionsim.send(connectionId, errorBack);
                            break;
                        }
                        byte[] ackBlo = new byte[4];
                        ackBlo[0] = 0;
                        ackBlo[1] = 4;
                        ackBlo[2] = (byte) ((blockN >> 8));
                        ackBlo[3] = (byte) (blockN & 0x00FF);
                        waitForData = 0;
                        connectionsim.send(connectionId, ackBlo);

                        // Bcast
                        byte[] fileNamen = idToFileUploadName.get(connectionId).getBytes(StandardCharsets.UTF_8);
                        byte[] bCast = new byte[4 + fileNamen.length];
                        bCast[0] = 0;
                        bCast[1] = 9;
                        bCast[2] = 1;
                        bCast[bCast.length - 1] = 0;
                        System.arraycopy(fileNamen, 0, bCast, 3, fileNamen.length);
                        Set<String> setOfKey = activeUsers.keySet();
                        for (String key : setOfKey) {
                            if (activeUsers.get(key)) {
                                int currClient = listOfUsersByName.get(key);
                                connectionsim.send(currClient, bCast);
                            }
                        }

                    }

                }

                break;

            case 5: // ERROR
                // ------------------ in our implementation we dont give client to send an ERROR

                break;

            case 6: // DIRQ
                if (activeUsersById.containsKey(connectionId) && activeUsersById.get(connectionId)) {
                    File fileFolder = new File("server" + "/" + "Files");
                    String[] fielsExist = new String[fileFolder.list().length];
                    // if there is no file we are sending empty packet
                    fielsExist = fileFolder.list();
                    List<Byte> byteList = new ArrayList<>();

                    // Adding opcode 03 to indicate data packet
                    byteList.add((byte) 0);
                    byteList.add((byte) 3);

                    // Adding file names
                    for (String f : fielsExist) {
                        byte[] data = f.getBytes(StandardCharsets.UTF_8);
                        for (byte b : data) {
                            byteList.add(b);
                        }
                        // Adding separator byte (0) between file names
                        byteList.add((byte) 0);
                    }
                    // converting from byte list to byte array:
                    byte[] dirqD = new byte[byteList.size()];
                    for (int i = 0; i < byteList.size(); i++) {
                        dirqD[i] = byteList.get(i);
                    }
                    int numOfPackets = dirqD.length / 512; // num of full packets
                    if ((dirqD.length) % 512 > 0) {
                        numOfPackets++; // adding one for the last that has less than 512
                    }

                    idToNumOfBlocks.remove(connectionId);
                    idToNumOfBlocks.put(connectionId, numOfPackets); // update num of expected ack
                                                                     // blocks
                    idToCurrExpectAck.remove(connectionId);
                    idToCurrExpectAck.put(connectionId, 1);
                    // making 512 packets , in case its more than one packet, the next packs will
                    // send through ack
                    if (numOfPackets > 1) {
                        byte[] packetSize = new byte[2];
                        packetSize[0] = (byte) ((512 >> 8));
                        packetSize[1] = (byte) (512 & 0x00FF); // casting 512 to bytes for all of the full packets
                        byte[] firstPack = new byte[518];
                        System.arraycopy(dirqD, 0, firstPack, 6, 512);
                        firstPack[0] = 0;
                        firstPack[1] = 3;
                        firstPack[2] = packetSize[0];
                        firstPack[3] = packetSize[1];
                        firstPack[4] = 0;
                        firstPack[5] = 1;

                        connectionsim.send(connectionId, firstPack);
                    } else { // case the data is less than 512 and can send with one try
                        byte[] firstPack = new byte[6 + dirqD.length];
                        System.arraycopy(dirqD, 0, firstPack, 6, dirqD.length);
                        firstPack[0] = 0;
                        firstPack[1] = 3;
                        byte[] packetSize = new byte[2];
                        packetSize[0] = (byte) ((dirqD.length >> 8));
                        packetSize[1] = (byte) (dirqD.length & 0x00FF);
                        firstPack[2] = packetSize[0];
                        firstPack[3] = packetSize[1];
                        firstPack[4] = 0;
                        firstPack[5] = 1;
                        connectionsim.send(connectionId, firstPack);
                    }
                } else {
                    String errorMsg = "User not logged in - Any opcode received before Login completes.";
                    byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                    byte[] errorBack = new byte[5 + errorMsgB.length];
                    errorBack[0] = 0;
                    errorBack[1] = 5;
                    errorBack[2] = 0;
                    errorBack[3] = 6;// the first four bytes-op and code error
                    errorBack[errorBack.length - 1] = 0;// the final byte=0
                    for (int i = 4; i < errorBack.length - 1; i++) {
                        errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                    }
                    connectionsim.send(connectionId, errorBack);

                }

                break;

            case 8: // DELRQ
                if (activeUsersById.containsKey(connectionId) && activeUsersById.get(connectionId)) {
                    byte[] fileNametoDel = Arrays.copyOfRange(message, 2, message.length);

                    String fileNameDel = new String(fileNametoDel, StandardCharsets.UTF_8);

                    Path pathToFile = Paths.get("server" + "/" + "Files" + "/" + fileNameDel);

                    boolean fileExists = Files.exists(pathToFile);

                    if (fileExists) { // if file exist:
                                      // delete the file from the Folder "Files":
                        try {
                            // delete:
                            Files.deleteIfExists(pathToFile);
                        } catch (Exception e) {
                            String errorMsg = "Access violation - File cannot be written, read or deleted.";
                            byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);
                            byte[] errorBack = new byte[5 + errorMsgB.length];
                            errorBack[0] = 0;
                            errorBack[1] = 5;
                            errorBack[2] = 0;
                            errorBack[3] = 2;
                            errorBack[errorBack.length - 1] = 0; // the final byte=0

                            for (int i = 4; i < errorBack.length - 1; i++) {
                                errorBack[i] = errorMsgB[i - 4]; // adding the corresponding String to the Error
                            }
                            connectionsim.send(connectionId, errorBack);
                        }
                        // return ACK 0 IF deleted:
                        byte[] ack0 = { 0, 4, 0, 0 };
                        connectionsim.send((connectionId), ack0);
                        // BCAST for all the client that this File is deleted:
                        byte[] fileNameDelArray = fileNameDel.getBytes(StandardCharsets.UTF_8);
                        byte[] bCast = new byte[4 + fileNameDelArray.length];
                        bCast[0] = 0;
                        bCast[1] = 9;
                        bCast[2] = 0; // deleted
                        bCast[bCast.length - 1] = 0;
                        System.arraycopy(fileNameDelArray, 0, bCast, 3, fileNameDelArray.length);
                        Set<String> setOfKey = activeUsers.keySet();
                        for (String key : setOfKey) {
                            if (activeUsers.get(key)) {
                                int currClient = listOfUsersByName.get(key);
                                connectionsim.send(currClient, bCast);
                            }
                        }
                    } else { // return error 1
                        String errorMsg = "File not found - RRQ DELRQ of non-existing file.";
                        byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);
                        byte[] errorBack = new byte[5 + errorMsgB.length];
                        errorBack[0] = 0;
                        errorBack[1] = 5;
                        errorBack[2] = 0;
                        errorBack[3] = 1;
                        errorBack[errorBack.length - 1] = 0;// the final byte=0

                        for (int i = 4; i < errorBack.length - 1; i++) {
                            errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                        }
                        connectionsim.send(connectionId, errorBack);
                    }
                } else {
                    String errorMsg = "User not logged in - Any opcode received before Login completes.";
                    byte[] errorMsgB = errorMsg.getBytes(StandardCharsets.UTF_8);

                    byte[] errorBack = new byte[5 + errorMsgB.length];
                    errorBack[0] = 0;
                    errorBack[1] = 5;
                    errorBack[2] = 0;
                    errorBack[3] = 6;// the first four bytes-op and code error
                    errorBack[errorBack.length - 1] = 0;// the final byte=0
                    for (int i = 4; i < errorBack.length - 1; i++) {
                        errorBack[i] = errorMsgB[i - 4];// adding the corresponding String to the Error
                    }
                    connectionsim.send(connectionId, errorBack);

                }

                break;

            default:
                break;
        }

    }

    @Override
    public boolean shouldTerminate() {

        // TODO implement this
        return shouldTerminate;

    }

}
