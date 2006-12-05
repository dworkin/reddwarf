package com.sun.sgs.client.comm;

/**
 * Some constants that help define the Project Darkstar Simple client/server
 * protocol.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public interface ProtocolMessage {

    public final int VERSION = 1;
    
    public final int AUTHENTICATION_REQUEST = 0x10;
    public final int LOGIN = 0x11;

    public final byte DIRECT_SEND = 0x20;
}
