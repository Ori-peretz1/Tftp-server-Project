package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.srv.BlockingConnectionHandler;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    // TODO: Implement here the TFTP encoder and decoder
    private byte[] bytes = new byte[1 << 1000]; // maximum size of one pack
    private int length = 0; // the actual size of the array

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // TODO: implement this
        if (length >= 2) {
            short opCode = (short) ((short) bytes[0] << 8 | (short) bytes[1]
                    & 0x00FF);

            switch (opCode) { // handling the decode by the OP and other unique features
                case 1:// ReadRq - RRQ
                case 2:// WriteRq - WRQ
                case 5:// ERROR
                case 7:// loginRq - LOGRQ
                case 8:// deletRq - DELRQ
                case 9:// BCAST - BROADCAST TO ALL CLIENTS
                    if (nextByte == 0 & length > 0) {
                        System.out.println(Arrays.toString(bytes));
                        return returnBytes();
                    }
                    break;

                case 4:// Acknowledge - ACK
                    if (length == 3) {
                        pushByte(nextByte);
                        return returnBytes();
                    }
                    break;
                case 3:// Data- DATA
                    int size = 0;
                    if (length >= 4) {
                        size = (short) ((short) bytes[2] << 8 | (short) bytes[3] & 0x00FF);

                    }
                    if (length == size + 5 | length == 518) {

                        System.out.println("the size of data " + size);
                        System.out.println("the length " + length);
                        pushByte(nextByte);
                        return returnBytes();
                    }

                    break;

                default:
                    break;
            }
        }

        pushByte(nextByte);
        System.out.println("SHIRSHUR");
        if (length >= 2 && bytes[1] == 10) { // opcode == 10 (DISC)
            return returnBytes();
        }
        if (length >= 2 && bytes[1] == 6) { // opcode == 6 (DIRQ)
            return returnBytes();
        }

        return null; // isnt complete yet
    }

    @Override
    public byte[] encode(byte[] message) {
        // TODO: implement this
        return message;
    }

    private void pushByte(byte nextByte) {
        if (length >= bytes.length) {
            bytes = Arrays.copyOf(bytes, length * 2);// extend the length and fill with the known bytes

        }
        bytes[length++] = nextByte; // adding the byte and update the length
        System.out.println(nextByte);
    }

    private byte[] returnBytes() {
        byte[] res = Arrays.copyOf(bytes, length);
        length = 0; // reset the length for the next use
        System.out.println(Arrays.toString(bytes));
        return res;

    }
}