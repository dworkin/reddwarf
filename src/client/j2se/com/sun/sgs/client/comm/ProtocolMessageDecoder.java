package com.sun.sgs.client.comm;

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
        String str = null;
        byte[] stringBytes = readBytes();
        try {
            str = new String(stringBytes, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
        }

        return str;
    }
    
    /**
     * Reads the next int off the buffer and returns true if it equals
     * 1, otherwise false.
     * 
     * @return true if the int read equals one.
     */
    public boolean readBoolean() {
        return readInt() == 1;
    }
    
    public int readInt() {
        return (readUnsignedByte() << 24) + 
                (readUnsignedByte() << 16) +
                (readUnsignedByte() << 8) +
                readUnsignedByte();
    }

    /**
     * Reads the next int off the given ByteBuffer, interpreting it as a
     * size, and reads that many more bytes from the buffer, returning
     * the resulting byte array.
     * 
     * @return a byte array matching the length of the first int read
     * from the buffer
     */
    public byte[] readBytes() {
        int length = readInt();
        byte[] bytes = new byte[length];
        System.arraycopy(data, index, bytes, 0, length);
        
        index += length;

        return bytes;
    }
    
    /**
     * Reads a regular old Java signed byte off the buffer and converts
     * it to an unsigned one (0-255).
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
     * @return the first byte as the version number of the protocol.
     */
    public int readVersionNumber() {
        return readUnsignedByte();
    }
    
    /**
     * Convenience method for reading the second byte as an unsigned byte.
     * 
     * @return the first byte as the command code of a message.
     */
    public int readCommand() {
        return readUnsignedByte();
    }

}
