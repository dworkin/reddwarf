package com.sun.sgs.client.simple;

/**
 * Some constants that help define the Project Darkstar Simple client/server
 * protocol.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
interface ProtocolMessage {

    final int VERSION = 1;
    
    // TBD what are all the possible services that could care about client
    // messages?  ChannelService? TaskService? DataService? 
    // AuthenticationService?
    
    
    /**
     * A general application level service.  
     */
    final int APPLICATION_SERVICE = 0x10;
    
    /**
     * A message bound for the channel service.
     */
    final int CHANNEL_SERVICE = 0x11;
    
    /**
     * Client to Server: a request of the server to login.  The username/password
     * credentials are supplied as part of the request.
     */
    final int LOGIN_REQUEST = 0x20;
    
    /**
     * Server to Client: notification that the client has successfully logged in
     */
    final int lOGIN_SUCCESS = 0x21;
    
    /**
     * Server to Client: notification that there was a problem with the login
     * process.  A reason for the failure is included in the message.
     */
    final int LOGIN_FAILURE = 0x22;
    
    /**
     * Server to Client: this acts as notification that the client has been 
     * logged out.
     */
    final int LOGOUT_SUCCESS = 0x23;
    
    /**
     * Client to Server: a request to gracefully logout of the server
     */
    final int LOGOUT_REQUEST = 0x24;
    
    /**
     * Server to Client: a general, application-specific message
     */
    final int MESSAGE_FROM_SERVER = 0x25;
    
    /**
     * Client to Server: a general, application-specific message
     */
    final int MESSAGE_TO_SERVER = 0x26;
    
    /**
     * Server to Client: notification that the client has been joined to a 
     * channel.
     */
    final int CHANNEL_JOIN = 0x27;
    
    
    /**
     * Server to Client: notification that a message has been received on a 
     * channel
     */
    final int CHANNEL_MESSAGE = 0x28;
    
    /**
     * Client to Server: request to send a message on a channel
     */
    final int CHANNEL_SEND_REQUEST = 0x29;
    
    /**
     * Server to Client: notification that the client has left a channel
     */
    final int CHANNEL_LEAVE = 0x2A;
    
    /**
     * Client to Server: request of the server to reconnect
     */
    final int RECONNECT_REQUEST = 0x2B;
    
    /**
     * Server to Client: notification that reconnection was successful
     */
    final int RECONNECT_SUCCESS = 0x2C;
    
    /**
     * Server to Client: notification that reconnection failed.  The reason
     * for failure is included in the message.
     */
    final int RECONNECT_FAILURE = 0x2D;
}
