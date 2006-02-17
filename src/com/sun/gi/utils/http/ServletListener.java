/*
 * ServletListener.java
 *
 * Created on January 5, 2006, 10:31 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.utils.http;

import java.util.Queue;
import javax.servlet.http.HttpSession;

/**
 *
 * @author as93050
 */
public interface ServletListener {
    /**
     * Handle any packets that arrived from the servlet. Return the outgoing message queue
     * that holds any messages that need to be returned to the client
     * @param data the data packets that arrived from the client
     * @param session the HttpSession which holds the SGSUser for this session
     * @return the outgoing message queue for this user which holds all the messages
     * that need to be sent back to the client.
     */
    public Queue<byte[]> dataArrived(byte[] data, HttpSession session);
   
   
}
