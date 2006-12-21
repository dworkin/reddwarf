package com.sun.sgs.client.simple;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience class for assembling a protocol message into bytes.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ProtocolMessageEncoder {
    
    private List<Object> list;
    
    public ProtocolMessageEncoder() {
        list = new ArrayList<Object>();
    }
    
    public void reset() {
        list.clear();
    }
    
    public void startMessage(int service, int command) {
        list.add(Integer.valueOf(ProtocolMessage.VERSION));
        list.add(Integer.valueOf(service));
        list.add(Integer.valueOf(command));
    }
    
    public void add(Object object) {
        list.add(object);
    }
    
    /**
     * Assembles the pieces of the message into a byte array.
     * 
     * @return  a byte array packed with the contents of the message
     */
    public byte[] getMessage() {
        List<Object> byteList = new ArrayList<Object>();
        // protocol version and command are first and second position respectively.
        byteList.add(getUnsignedByte((Integer) list.get(0)));
        byteList.add(getUnsignedByte((Integer) list.get(1)));
        byteList.add(getUnsignedByte((Integer) list.get(2)));
        int bufferSize = 3;

        // this first pass is twofold: to get the buffer size, and to
        // convert the input list to lengths (in ints) and byte arrays.
        for (int i = 3; i < list.size(); i++) {
            Object curObj = list.get(i);

            // if the obj is null, translate that to a zero length
            // "nothing" as a placeholder.
            if (curObj == null) {
                bufferSize += 4;
                byteList.add(0);
                continue;
            }
            if (curObj instanceof String) {
                String str = (String) curObj;
                try {
                    byte[] strBytes = str.getBytes("UTF-8");
                    
                    int len = strBytes.length;
                    if (len > Short.MAX_VALUE)
                        throw new IllegalStateException(
                                "String too long: " + len);

                    byteList.add(Short.valueOf((short) len));
                    byteList.add(strBytes);
                    bufferSize += 2 + len;
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else if (curObj instanceof Integer) {
                byteList.add(curObj);
                bufferSize += 4;
            } else if (curObj instanceof Short) {
                byteList.add(curObj);
                bufferSize += 2;
            } else if (curObj instanceof byte[]) {
                byte[] array = (byte[]) curObj;
                byteList.add(Short.valueOf((short) array.length));
                byteList.add(array);
                bufferSize += array.length + 2;
            } else {
                throw new IllegalStateException("Unsupported type: " +
                        curObj.getClass().getName());
            }
        }

        // now that we know the buffer size, allocate the buffer
        // and pack it with the contents of the byte list.
        byte[] buffer = new byte[bufferSize];
        int index = 0;
        for (Object curObj : byteList) {
            if (curObj instanceof Byte) {
                buffer[index] = ((Byte) curObj);
                index++;
            } else if (curObj instanceof Integer) {
                putInt((Integer) curObj, buffer, index);
                index += 4;
            } else if (curObj instanceof Short) {
                putShort((Short) curObj, buffer, index);
                index += 2;
            } else if (curObj instanceof byte[]) {
                byte[] array = (byte[]) curObj;
                System.arraycopy(array, 0, buffer, index, array.length);
                index += array.length;
            }
        }
        
        return buffer;
    }
    
    /**
     * Packs an int value into the given buffer at the given offset. 
     * 
     * @param value
     * @param data
     * @param offset
     */
    private void putInt(int value, byte[] data, int offset) {
        data[offset    ] = (byte) (value >>> 24); 
        data[offset + 1] = (byte) (value >>> 16); 
        data[offset + 2] = (byte) (value >>> 8); 
        data[offset + 3] = (byte)  value; 
    }

    private void putShort(short value, byte[] data, int offset) {
        data[offset    ] = (byte) (value >>> 8); 
        data[offset + 1] = (byte)  value; 
    }

    private void putChar(char value, byte[] data, int offset) {
        data[offset    ] = (byte) (value >>> 8); 
        data[offset + 1] = (byte)  value; 
    }
    
    /**
     * Converts the given int to an unsigned byte (by downcasting).
     * 
     * @param b the byte stored as an int
     * 
     * @return the same value returned as a byte unsigned.
     */
    private Byte getUnsignedByte(int b) {
        return Byte.valueOf((byte) b);
    }

}
