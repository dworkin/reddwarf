package com.sun.sgs.impl.client.simple;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Convenience class for translating protocol messages from bytes into their 
 * component parts.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ProtocolMessageDecoder {
    
    private DataInputStream inputStream;

    /**
     * Create a decoder for the given {@code message}.
     * 
     * @param message the data to decode.
     */
    public ProtocolMessageDecoder(byte[] message) {
        inputStream = new DataInputStream(new ByteArrayInputStream(message));
    }
    
    /**
     * Reads a String from the byte stream as Modified UTF-8 at the current
     * position.
     * 
     * @return a Modified UTF-8 string
     */
    public String readString() {
        String str = null;
        try {
            str = inputStream.readUTF();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        
        return str;

    }
    
    /**
     * Reads the next int off the byte stream and returns true if it equals
     * 1, otherwise false.
     * 
     * @return true if the int read equals one.
     */
    public boolean readBoolean() {
        boolean b = false;
        try {
            b = inputStream.readBoolean();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return b;
    }

    /**
     * Reads the next 8 bytes off the byte stream at the current position
     * and returns a network byte ordered long.
     * 
     * @return the next 8 bytes as a long
     */
    public long readLong() {
        long num = 0;
        try {
            num = inputStream.readLong();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return num;
    }
    
    /**
     * Reads the next 4 bytes from the byte stream at the current position
     * and returns a network byte ordered int. 
     * 
     * @return the next 4 bytes as an int
     */
    public int readInt() {
        int num = 0;
        try {
            num = inputStream.readInt();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return num;
    }
    
    /**
     * Reads the next 2 bytes from the byte stream at the current position
     * and returns a network byte ordered short.
     * 
     * @return the next 2 bytes as a short
     */
    public short readShort() {
        short num = 0;
        try {
            num = inputStream.readShort();
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        return num; 
    }

    /**
     * Reads the next short from the input stream, interpreting it as a
     * size, and reads that many more bytes from the stream, returning
     * the resulting byte array.
     * 
     * @return a byte array matching the length of the first short read
     * from the stream, or {@code null} if the length is zero.
     */
    public byte[] readBytes() {        
        try {
            short len = inputStream.readShort();
            if (len == 0) {
                return null;
            }
            byte[] bytes = new byte[len];
            inputStream.read(bytes);
            return bytes;
        } catch (IOException ioe) {
            return null;
        }
    }
    
    /**
     * Reads a Java signed byte off the buffer and converts it to an unsigned 
     * one (0-255).
     * 
     * @return the unsigned representation of the next byte off the
     * buffer (as an int).
     */
    public int readUnsignedByte() {
        int uByte = 0;
        
        try {
            uByte = inputStream.read() &  0xff;
        }
        catch (IOException ioe) {
            // not thrown by the input stream
        }
        
        return uByte;
    }
    
    /**
     * Convenience method for reading the first byte as the version number.
     * 
     * @return the first byte as the version number of the protocol.
     */
    public int readVersionNumber() {
        return readUnsignedByte();
    }
    
    /**
     * Convenience method for reading the third byte as the command op code.
     * 
     * @return the third byte as the command code of the message.
     */
    public int readCommand() {
        return readUnsignedByte();
    }

    /**
     * Convenience method for reading the second byte as the service number.
     * 
     * @return the second byte as the service number.
     */
    public int readServiceNumber() {
        return readUnsignedByte();
    }

}
