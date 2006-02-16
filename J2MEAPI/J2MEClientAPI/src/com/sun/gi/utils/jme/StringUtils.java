/*
 * StringUtils.java
 *
 * Created on February 14, 2006, 10:12 AM
 *
 *
 */

package com.sun.gi.utils.jme;

/**
 *
 * @author as93050
 */
public class StringUtils {
    
    /** Creates a new instance of StringUtils */
    public static String bytesToHex(byte[] bytes,int start, int length){
        StringBuffer buff = new StringBuffer();
        for(int i=0;i<length;i++){
            buff.append(byteToHex(bytes[i+start]));
        }
        return buff.toString();
    }
    
    public static String bytesToHex(byte[] bytes){
        return bytesToHex(bytes,0,bytes.length);
    }
    
    private static String[] hexDigits = {"A","B","C","D","E","F"};
    public static String byteToHex(byte b){
        return halfByteToHex((byte)(b>>4))+halfByteToHex((byte)(b&0xF));
    }
    
    private static String halfByteToHex(byte b){
        b &= (byte)0xF;
        if (b<10) return String.valueOf((int)b);
        return hexDigits[b-10];
    }
    
    
}
