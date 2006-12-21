package com.sun.sgs.client.simple;

import java.io.UnsupportedEncodingException;

/**
 * Translates protocol messages from bytes into their component parts.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ProtocolMessageDecoder {
    
    private byte[] data;
    private int index = 0;
    
    public ProtocolMessageDecoder() {
    }
    
    public void setMessage(byte[] message) {
        this.data = message;
        index = 0;
    }
    
    public String readString() {
        byte[] strBytes = readBytes();
        try {
            return new String(strBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    public int readInt() {
        return (readUnsignedByte() << 24) + 
               (readUnsignedByte() << 16) +
               (readUnsignedByte() << 8) +
                readUnsignedByte();
    }
    
    public short readShort() {
        return (short) ((readUnsignedByte() << 8) +
                         readUnsignedByte());
    }

    public char readChar() {
        return (char) ((readUnsignedByte() << 8) +
                        readUnsignedByte());
    }
    
    /**
     * Reads the next short off the given ByteBuffer, interpreting it as a
     * size, and reads that many more bytes from the buffer, returning
     * the resulting byte array.
     * 
     * @param data the buffer to read from
     * 
     * @return a byte array matching the length of the first short read
     * from the buffer
     */
    public byte[] readBytes() {
        int length = readShort();
        byte[] bytes = new byte[length];
        System.arraycopy(data, index, bytes, 0, length);
        
        index += length;

        return bytes;
    }
    
    /**
     * Reads a regular old Java signed byte off the buffer and converts
     * it to an unsigned one (0-255).
     * 
     * @param data the buffer from which to read
     * 
     * @return the unsigned representation of the next byte off the
     * buffer (as an int).
     */
    public int readUnsignedByte() {
        int uByte = data[index] & 0xff;
        
        index++;
        
        return uByte;
    }
    
    /**
     * Convenience method for reading the first byte as the version number.
     * 
     * @param data      the data to read from
     * 
     * @return the first byte as the version number of the protocol.
     */
    public int readVersionNumber() {
        return readUnsignedByte();
    }

    public int readService() {
        return readUnsignedByte();
    }
    
    /**
     * Convenience method for reading the second byte as an unsigned byte.
     * 
     * @param data      the data to read from
     * 
     * @return the first byte as the command code of a message.
     */
    public int readCommand() {
        return readUnsignedByte();
    }

}
