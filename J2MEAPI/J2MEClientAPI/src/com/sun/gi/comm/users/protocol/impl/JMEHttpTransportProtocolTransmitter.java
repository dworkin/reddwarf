/*
 * JMEHttpTransportProtocolTransmitter.java
 *
 * Created on January 30, 2006, 9:45 AM
 *
 *
 */

package com.sun.gi.comm.users.protocol.impl;

import com.sun.gi.comm.users.client.impl.JMEHttpTransporter;
import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.LinkedQueue;

/**
 *
 * @author as93050
 */
public class JMEHttpTransportProtocolTransmitter  {
    
    private JMEHttpTransporter httpTransporter;
    private LinkedQueue queue;
    private boolean transporterStopped = true;
    
    /** Creates a new instance of TestTransportProtocolTransmitter */
    public JMEHttpTransportProtocolTransmitter() {
        httpTransporter = new JMEHttpTransporter();
        queue = new LinkedQueue();
        httpTransporter.setInputQueue(queue);
    }
    
    private void startTransporterThread() {
        httpTransporter.setStop(false);
        Thread transporterThread = new Thread(httpTransporter);
        transporterThread.start();
        transporterStopped = false;
    }
    
    public void sendBuffers(ByteBuffer[] buffs) {
        queue.enqueue(buffs);
        if (transporterStopped) {
            startTransporterThread();
        }
    }
    
    /**
     * Since we don't have persistent connections there is no connection to
     * close. However, we need to tell the server that we are logging off and
     * stop the transporter thread
     */
    public void closeConnection() {
        Runnable logout = new Runnable() {
            public void run() {
                if (!transporterStopped) {
                    
                    stopTransporterThread();
                    
                }
                httpTransporter.sendLogoutRequest();
            }
        };
        Thread logoutThread = new Thread(logout);
        logoutThread.start();
        
    }
    
    private void stopTransporterThread() {
        try {
            synchronized(httpTransporter) {
                httpTransporter.wait();
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        httpTransporter.setStop(true);
        transporterStopped = true;
    }
    
    public void setGameName(String gameName) {
        httpTransporter.setGameName(gameName);
    }
    
    public void setHost(String host) {
        httpTransporter.setHost(host);
    }
    
    public void setPort(String port) {
        httpTransporter.setPort(port);
    }
    
    public void setPollInterval(long interval) {
        httpTransporter.setPollInterval(interval);
    }
    
    public void stopPolling() {
        stopTransporterThread();
    }
    
}
