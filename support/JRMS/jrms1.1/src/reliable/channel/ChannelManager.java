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
 * ChannelManager.java
 */
package com.sun.multicast.reliable.channel;

import com.sun.multicast.reliable.RMException;

/**
 * A reliable multicast channel manager.  Several different implementations 
 * of the ChannelManager interface are provided, depending on whether it's 
 * local or remote.
 * 
 * This interface includes methods that are shared by both secondary and primary
 * channel managers.  It is extended by the PrimaryChannelManager interface, 
 * which adds methods unique to primary channel managers.
 * 
 * To get a ChannelManager object, use LocalPCM.getLocalPCM().
 * 
 * @see                         PrimaryChannelManager
 * @see                         LocalPCM
 */
public interface ChannelManager extends java.rmi.Remote {

    /**
     * Authenticate with the ChannelManager.
     * 
     * @param identity the identity to be established
     * @param key the authorization key to be used
     * @return <code>true</code> if authentication succeeded;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean authenticate(String identity, String key) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Get the number of channels accessible to this principal on this channel 
     * manager.
     * @return the number of channels accessible to this principal on this 
     * channel manager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getChannelCount() throws RMException, java.rmi.RemoteException;

    /**
     * Get an array of channel IDs for all the channels accessible to this 
     * principal on this channel manager that match the channel and 
     * application names given.
     * @param channelName channel name to match (null to match any)
     * @param applicationName application name to match (null to match any)
     * @return an array of channel IDs
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    long[] getChannelList(String channelName, String applicationName) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Get the Channel that goes with a given channel ID.
     * @param channelID channel ID whose Channel is requested
     * @return the Channel that was requested
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception com.sun.multicast.reliable.channel.ChannelNotFoundException 
     * if there is no Channel with the given ID
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Channel getChannel(long channelID) 
            throws RMException, ChannelNotFoundException, 
                   java.rmi.RemoteException;

    /**
     * Get the number of registered receivers for the ChannelManager.
     * 
     * @return the number of registered receivers for this ChannelManager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getRegisteredReceiverCount() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Get an array of principal names for all the registered receivers
     * on this channel manager.
     * 
     * @return an array of principal names
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String[] getRegisteredReceiverList() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Get the number of registration failures for the ChannelManager.
     * 
     * @return the number of registration failures for the ChannelManager
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getRegistrationFailureCount() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets whether the channel manager is enabled. Disabled channel managers 
     * are not accessible to anyone without administrator access.
     * 
     * @param b if <code>true</code>, enable the channel manager;
     * otherwise, disable it
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setEnabled(boolean b) throws RMException, java.rmi.RemoteException;

    /**
     * Tests whether the channel manager is enabled. Disabled channel managers 
     * are not accessible to anyone without administrator access.
     * 
     * @returns <code>true</code> if the channel manager is enabled;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean isEnabled() throws RMException, java.rmi.RemoteException;

    /**
     * Add a ChannelListChangeListener to the listener list.
     * @param listener the listener to be added
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void addChannelListChangeListener(ChannelListChangeListener listener) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Remove a ChannelListChangeListener from the listener list.
     * @param listener the listener to be removed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void removeChannelListChangeListener(ChannelListChangeListener listener) 
            throws RMException, java.rmi.RemoteException;

}
