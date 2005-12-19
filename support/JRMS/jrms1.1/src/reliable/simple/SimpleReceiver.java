/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * SimpleReceiver.java
 */
package com.sun.multicast.reliable.simple;

import java.io.InputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnimplementedOperationException;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.ChannelNotFoundException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.util.UnsupportedException;

/**
 * A simple receiver object. This class provides a single class that
 * lets you receive data as simply as possible. Multiple senders
 * per channel, security, and out of order delivery are not supported.
 */
public class SimpleReceiver {

    /**
     * Creates a SimpleReceiver with the parameters given. This constructor 
     * finds the channel using the channel and application names provided.
     * @param applicationName the name of the application
     * @param channelName the name of the channel
     * @exception com.sun.multicast.reliable.channel.ChannelNotFoundException 
     * if no channel is found.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    public SimpleReceiver(String applicationName, String channelName) 
	throws ChannelNotFoundException, RMException, RemoteException {
	System.out.println("Creating Primary Channel Manager.");

	System.out.println("Getting array of channels from ChannelList.");

        cm = (PrimaryChannelManager) 
	    ChannelManagerFinder.getChannelManager(null);

        long[] clist = cm.getChannelList(channelName, applicationName);

	System.out.println("Making sure that we get at least 1 Channel.");

        try {
            while ((clist.length < 1)) {
                Thread.sleep(100);

                clist = cm.getChannelList(channelName, applicationName);
            }
        } catch (InterruptedException e) {}

        if (clist.length < 1) {
            throw new ChannelNotFoundException();
        } 

	System.out.println("Finally grabbing that one channel.");

        c = cm.getChannel(clist[0]);

	System.out.println("Creating a TRAMTransportProfile.");
        tp = (TRAMTransportProfile) c.getTransportProfile();
    }
    
    /**
     * Creates a SimpleReceiver from a serialized channel store in a file.
     * @param channelFileName the name of the file that stores the channel
     * @exception com.sun.multicast.reliable.channel.ChannelNotFoundException 
     * if no channel is found.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
    */
    public SimpleReceiver(String channelFileName) 
	throws ChannelNotFoundException, RMException {

        try {
	    System.out.println("Creating a SimpleReceiver from a channelFile.");
            cm = (PrimaryChannelManager) 
		ChannelManagerFinder.getChannelManager(null);
            c = (Channel) cm.readChannel(channelFileName);
            tp = (TRAMTransportProfile) c.getTransportProfile();
            // tp.setLogMask(TRAMTransportProfile.LOG_VERBOSE);
	} catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    /**
     * Returns an InputStream object that may be used to receive data.
     * @return an InputStream object
     * @exception UnsupportedException if the TransportProfile
     * does not support stream sockets.
     * @exception InvalidTransportProfileException if the TransportProfile is
     * invalid
     * @exception java.io.IOException if an I/O error occurs.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    public InputStream getInputStream() throws 
	UnsupportedException, IOException, InvalidTransportProfileException, 
	RMException {

        ms = tp.createRMStreamSocket(TransportProfile.RECEIVER);

        return (ms.getInputStream());
    }

    /**
     * Leaves the multicast transport session gracefully.
     * This method may take some time to return.
     */
    public void close() {
        ms.close();
    }

    public TRAMTransportProfile getTRAMTransportProfile() {
	return tp;
    }

    private PrimaryChannelManager cm;
    private Channel c;
    // made this a tram transport profile so we can switch on logging
    private TRAMTransportProfile tp;
    private RMStreamSocket ms;

}
