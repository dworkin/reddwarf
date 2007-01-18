package com.sun.sgs.impl.client.simple;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.sgs.client.SessionId;

/**
 * Convenience class for assembling a protocol message into bytes.  Clients 
 * should call the writeX methods to build their message, and getMessage()
 * to obtain the resulting byte array.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ProtocolMessageEncoder {
    
    private DataOutputStream outputStream;
    private ByteArrayOutputStream byteStream;
    
    /**
     * Starts assembling a message with the given service id number and op code. 
     * Op Codes and service id numbers are defined in {@link ProtocolMessage}.
     * 
     * @param service           the id of the server-side service that should
     *                          interpret this message 
     * 
     * @param command           the op code identifying the remainder of the 
     *                          message 
     */
    public ProtocolMessageEncoder(int service, int command) {
        outputStream = new DataOutputStream(byteStream = 
                                                new ByteArrayOutputStream());
        writeUnsignedByte(ProtocolMessage.VERSION);
        writeUnsignedByte(service);
        writeUnsignedByte(command);
    }
    
    
    /**
     * Returns the underlying byte array of the outgoing message.
     * 
     * @return a byte array containing the encoded message
     */
    public byte[] getMessage() {
        return byteStream.toByteArray();
    }
    
    /**
     * Writes the given int to the output stream as an unsigned byte.
     * 
     * @param b         the int to write out as a byte
     */
    private void writeUnsignedByte(int b) {
        try {
            outputStream.writeByte(b);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }
    
    /**
     * Writes the given boolean to the output stream as a byte of value one 
     * (if true), or zero (if false).
     * 
     * @param b                 the boolean to write out
     */
    public void writeBoolean(boolean b) {
        try {
            outputStream.writeBoolean(b);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }
    
    /**
     * Writes the given byte array to the output stream, prefaced by its
     * length as a short.
     * 
     * @param bytes             the byte array to write
     */
    public void writeBytes(byte[] bytes) {
        try {
            outputStream.writeShort(bytes.length);
            outputStream.write(bytes);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }
    
    /**
     * Writes the given String to the output stream in the Modified UTF-8 
     * encoding.
     * 
     * @param string    the String to write
     */
    public void writeString(String string) {
        try {
            outputStream.writeUTF(string);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }

    /**
     * Writes the given long to the output stream in network byte order.
     * 
     * @param l         the long to write
     */
    public void writeLong(long l) {
        try {
            outputStream.writeLong(l);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }
    
    /**
     * Writes the given int to the output stream in network byte order.
     * 
     * @param i         the int to write
     */
    public void writeInt(int i) {
        try {
            outputStream.writeInt(i);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }
    
    
    /**
     * Writes the given int to the output stream as a short in network byte 
     * order.
     * 
     * @param num       the int to write as a short
     */
    public void writeShort(int num) {
        try {
            outputStream.writeShort(num);
        }
        catch (IOException ioe) {
            // not thrown by the output stream
        }
    }
    
    /**
     * Writes the given SessionId to the output stream.  This is the same as
     * writing a byte array. 
     * 
     * @param id        the SessionId to write
     */
    public void writeSessionId(SessionId id) {
        writeBytes(id.toBytes());
    }

}
