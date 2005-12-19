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
 * PrimaryChannelManager.java
 */
package com.sun.multicast.reliable.channel;

import com.sun.multicast.reliable.RMException;

/**
 * A primary channel manager.  Several different implementations of the
 * PrimaryChannelManager interface may be provided (local, remote, etc.).
 * 
 * This interface includes methods unique to primary channel managers. 
 * It extends the ChannelManager interface, which includes methods that are 
 * shared by both secondary and primary channel managers.
 * 
 * To get a PrimaryChannelManager object, use LocalPCM.getLocalPCM().
 * 
 * @see                         ChannelManager
 * @see                         LocalPCM
 */
public interface PrimaryChannelManager extends ChannelManager {

    /**
     * Create a new Channel. The channel is initially disabled, with all fields
     * set to null, 0, or false (except the channel ID, which is initialized to
     * a random value; and
     * the creation time, which is set to the current time).
     * @return the new Channel
     * @exception com.sun.multicast.reliable.channel.LimitExceededException 
     * if the PCM's channel limit has been reached
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Channel createChannel() 
            throws LimitExceededException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Set the maximum number of channels that this PCM can own.
     * @param limit the new channel limit
     * @exception com.sun.multicast.reliable.channel.LimitExceededException 
     * if the PCM already
     * has more channels than the new limit
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setChannelLimit(int limit) 
            throws LimitExceededException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Gets the maximum number of channels that this PCM can own.
     * @returns the channel limit
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getChannelLimit() throws RMException, java.rmi.RemoteException;

    /**
     * Adds a principal to the list of secondary channel managers (SCMs) 
     * authorized to communicate with this channel manager.
     * 
     * @param principal the principal name to be added
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void addSCM(String principal) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Removes a principal from the list of secondary channel managers (SCMs) 
     * authorized to communicate with this channel manager.
     * 
     * @param principal the principal name to be removed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void removeSCM(String principal) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets the number of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * 
     * @return the number of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getSCMCount() throws RMException, java.rmi.RemoteException;

    /**
     * Gets a copy of the list of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * This list is a snapshot, so it will not be updated if authorizations 
     * change.
     * 
     * @return the list of secondary channel managers (SCMs) authorized
     * to communicate with this channel manager.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String[] getSCMList() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the number of channels owned by this channel manager.
     * 
     * @return the number of channels owned by this channel manager.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getOwnedChannelCount() throws RMException, java.rmi.RemoteException;

    /**
     * Gets a list of channel IDs for channels owned by this channel manager.
     * This list is a snapshot, so it will not be updated if the channel list 
     * changes.
     * 
     * @return the list of channel ID for channels owned by this channel manager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    long[] getOwnedChannelList() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Read a serialized channel from a file.
     * @param fileName the name of the file to store the channel
     * @return channel read from file
     * @exception java.io.IOException is raised if failure to read from file
     */
    
    Channel readChannel(String fileName)
    	    throws java.io.IOException, ClassNotFoundException;

    /**
     * Serialize and store a channel in a file.
     * @param channel the channel to be stored in a file
     * @param fileName the name of the file to store the channel
     * @exception java.io.IOException is raised if failure to write to file
     */
    public void fileChannel(Channel channel, String fileName)
    	    throws java.io.IOException;

}
