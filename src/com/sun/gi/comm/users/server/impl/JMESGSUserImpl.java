/*
 * JMESGSUserImpl.java
 *
 * Created on January 19, 2006, 9:19 AM
 *
 *
 */

package com.sun.gi.comm.users.server.impl;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.validation.UserValidator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * An extension of SGSUserImpl which adds a queue and a batchprocessor to the user
 * The queue is used to hold any messages that need to be sent to the 
 * user, the batchprocessor is used to process multiple packets
 * @author as93050
 */
public class JMESGSUserImpl extends SGSUserImpl {
    private JMEBatchProcessor batchProcessor;
    private Queue<byte[]> outgoingMessageQueue;
    /** Creates a new instance of JMESGSUserImpl */
    public JMESGSUserImpl(Router router, TransportProtocolTransmitter xmitter,
			UserValidator[] validators) {
        super(router,xmitter,validators);
        batchProcessor = (JMEBatchProcessor)xmitter;
        outgoingMessageQueue = new LinkedList<byte[]>();
    }
    
    protected JMEBatchProcessor getBatchProcessor() {
        return batchProcessor;
    }
    
    protected Queue<byte[]> getOutgoingMessageQueue() {
        return outgoingMessageQueue;
    }
}
