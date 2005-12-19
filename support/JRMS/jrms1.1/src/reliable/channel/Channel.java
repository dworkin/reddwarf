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
 * Channel.java
 */
package com.sun.multicast.reliable.channel;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.Vector;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;

/**
 * A reliable multicast channel.  Several different implementations of the 
 * Channel interface are provided, depending on whether it's local or remote. 
 * To get a Channel object, use ChannelManager.getChannel() or 
 * ChannelManager.createChannel().
 * 
 * @see                         ChannelManager
 */
public interface Channel extends java.rmi.Remote {

    /**
     * Destroys the <code>Channel</code>.  This causes all ChannelManagers 
     * to forget about the Channel.
     * The transport should not be active when destroy is called.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs 
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void destroy() throws RMException, java.rmi.RemoteException;

    /**
     * Duplicates the <code>Channel</code>. Creates a new channel exactly 
     * like this one, but with a new channel ID.
     * @return the new channel
     * @exception com.sun.multicast.reliable.channel.LimitExceededException 
     * if the PCM's channel limit has been reached 
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Channel duplicate() 
            throws LimitExceededException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Throws an exception if the channel is not valid. A channel is valid if, 
     * as far as can be determined, the channel could be used to send and 
     * receive data (subject to access control restrictions).
     * 
     * <P> Possible causes for invalid channels include not providing a 
     * transport profile or requesting support for multiple senders and 
     * providing a transport profile that cannot support this.
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is not valid
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void validate() 
            throws InvalidChannelException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Creates a ChannelRMStreamSocket for sending and/or receiving on the 
     * channel.
     * 
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new ChannelRMStreamSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     * @exception UnsupportedException if the
     * underlying  transport does not support a stream interface.
     */
    ChannelRMStreamSocket createRMStreamSocket(int sendReceive) 
            throws RMException, java.io.IOException, 
                   java.rmi.RemoteException, UnsupportedException;

    /**
     * Creates a ChannelRMStreamSocket for sending and/or receiving on
     * the channel.
     * 
     * @param tp a transport profile to use in creating the socket. Great
     * care must be taken when supplying a modified transport profile to
     * a channel that already has one defined. Some modifications to the
     * profile may prohibit communication with other members of the group.
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new ChannelRMStreamSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     * @exception UnsupportedException if the
     * underlying  transport does not support a stream interface.
     */
    ChannelRMStreamSocket createRMStreamSocket(TransportProfile tp, 
        int sendReceive) throws RMException, java.io.IOException, 
	java.rmi.RemoteException, UnsupportedException;

    /**
     * Creates a ChannelRMPacketSocket for sending and/or receiving on
     * the channel.
     * 
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new ChannelRMPacketSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     * @exception IOException if an I/O error occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     * @exception UnsupportedException if the
     * underlying  transport does not support a packet interface.
     */
    ChannelRMPacketSocket createRMPacketSocket(int sendReceive) 
            throws RMException, java.rmi.RemoteException, IOException, 
                   UnsupportedException;

    /**
     * Creates a ChannelRMPacketSocket for sending and/or receiving on 
     * the channel.
     * 
     * @param tp a transport profile to use in creating the socket. Great
     * care must be taken when supplying a modified transport profile to
     * a channel that already has one defined. Some modifications to the
     * profile may prohibit communication with other members of the group.
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new ChannelRMPacketSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     * @exception IOException if an I/O error occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     * @exception UnsupportedException if the
     * underlying  transport does not support a packet interface.
     */
    ChannelRMPacketSocket createRMPacketSocket(TransportProfile tp, 
	int sendReceive) throws RMException, java.rmi.RemoteException, 
	IOException, UnsupportedException;

    /**
     * Add a ChannelChangeListener to the listener list.
     * @param listener the listener to be added
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void addChannelChangeListener(ChannelChangeListener listener) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Remove a ChannelChangeListener from the listener list.
     * @param listener the listener to be removed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void removeChannelChangeListener(ChannelChangeListener listener) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel name.
     * null means unknown or unspecified.
     * @param name the new channel name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setChannelName(String name) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's application name.
     * null means unknown or unspecified.
     * @param name the new application name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setApplicationName(String name) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's transport profile. Because the Channel object may be
     * remote, the channel actually makes a copy of the TransportProfile 
     * provided and uses that.
     * null means unknown or unspecified.
     * @param profile the new transport profile
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is enabled and this would cause it to become invalid
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setTransportProfile(TransportProfile profile) 
            throws InvalidChannelException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Sets the channel's data start time. This date need not be set and if set 
     * is only advisory.
     * Data transmission may start at any time at the discretion of the 
     * channel's sender(s).  Because the channel may be remote, the channel 
     * actually makes a copy of the Date provided and uses that.
     * null means unknown or unspecified.
     * @param startTime the new data start time
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setDataStartTime(Date startTime) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's data end time. This date need not be set and if set 
     * is only advisory.  Data transmission may end at any time at the 
     * discretion of the channel's sender(s).
     * Because the channel may be remote, the channel actually makes a copy 
     * of the Date provided and uses that.
     * null means unknown or unspecified.
     * @param endTime the new data end time
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setDataEndTime(Date endTime) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's session end time. This is a time by which the 
     * multicast transport session associated with a channel is expected to end.
     * This date need not be set and if set is only advisory.
     * Because the channel may be remote, the channel actually makes a copy of 
     * the Date provided and uses that.
     * null means unknown or unspecified.
     * @param endTime the new session end time
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setSessionEndTime(Date endTime) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's suggested registration lead time. This is the time 
     * before the data start time at which receivers may begin to register.
     * This value need not be set and if set is only advisory.
     * 
     * @param leadTime lead time in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setLeadTime(int leadTime) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's suggested registration randomizer interval. This 
     * value specifies the period of time after the registration lead time 
     * over which registrations should be spread in order to spread out channel 
     * registration activity.  This value need not be set and if set is only 
     * advisory.
     * 
     * @param interval randomizer interval in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setLeadTimeRandomInterval(int interval) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the channel's expiration time. This is a time at which these channel
     * characteristics expire. The receiver should have reregistered before 
     * this time and begun using the new characteristics received during 
     * reregistration.  This date need not be set and if set is only advisory.
     * Because the channel may be remote, the channel actually makes a copy 
     * of the Date provided and uses that.
     * null means unknown or unspecified.
     * 
     * @param expirationTime the new channel expiration time
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setExpirationTime(Date expirationTime) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the minimum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually drop below this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @param speed the new minimum speed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setMinimumSpeed(int speed) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the maximum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually rise above this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @param speed the new minimum speed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setMaximumSpeed(int speed) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets an optional field for additional channel-specific data to be 
     * included in advertisements. As much of this field as possible is 
     * included in advertisements,
     * up to about 600 bytes.
     * null means unknown or unspecified.
     * @param data the new additional advertised data
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setAdditionalAdvertisedData(String data) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets an optional field for additional channel-specific data to be 
     * included in the channel, but not included in advertisements.
     * null means unknown or unspecified.
     * @param data the new additional unadvertised data
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setAdditionalUnadvertisedData(String data) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets an optional field containing an abstract describing the channel. 
     * As much of this field as possible is included in advertisements, up 
     * to about 600 bytes.
     * null means unknown or unspecified.
     * @param data the new abstract
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setAbstract(String data) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets an optional field containing a contact name for the channel.
     * null means unknown or unspecified.
     * @param data the new contact name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setContactName(String data) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets whether the channel is enabled. Disabled channels are not 
     * accessible to anyone without administrator access. Enabled channels 
     * must be valid (as described under the validate method).
     * @param b if <code>true</code>, enable the channel;
     * otherwise, disable it
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is invalid and enabling was requested
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setEnabled(boolean b) 
            throws InvalidChannelException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Sets whether channel advertising is requested.
     * 
     * <P><strong>NOTE:</strong> A channel is only advertised if it is enabled 
     * and channel advertising has been requested.
     * @param advertising <code>true</code> if the channel should be advertised;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setAdvertisingRequested(boolean newAdvertising) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Sets the requested multicast address to be used for advertisements.
     * The value null means use the default advertisement address.
     * Note that the TTL used for advertisements is the same as that specified 
     * in the TransportProfile.
     * @param address the new multicast address
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.io.IOException if the address is not a multicast address
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setAdvertisementAddress(InetAddress address) 
            throws RMException, java.io.IOException, 
                   java.rmi.RemoteException;

    /**
     * Sets if multiple senders are allowed on this channel.
     * @param multiple <code>true</code> if multiple senders should be allowed 
     * on this channel;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is enabled and this would cause it to become invalid
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setMultipleSendersAllowed(boolean multiple) 
            throws InvalidChannelException, RMException, 
                   java.rmi.RemoteException;

    /**
     * Sets the channel's dynamic filter list. Because the Channel object may be
     * remote, the channel actually makes a deep copy of the list provided
     * and uses that. null means none.
     * 
     * <P>The dynamic filters in this list are passed to the receiver, where
     * they are connected together using their setDataSource methods. The
     * first dynamic filter in the list is applied to the data last.
     * @param list the new dynamic filter list
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    void setDynamicFilterList(Vector list) 
            throws RMException, java.rmi.RemoteException;

    /**
     * Returns the channel name.
     * null means unknown or unspecified.
     * @return the channel name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String getChannelName() throws RMException, java.rmi.RemoteException;

    /**
     * Returns the channel's application name.
     * null means unknown or unspecified.
     * @return the channel's application name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String getApplicationName() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Returns a copy of the channel's TransportProfile. Because the profile
     * returned is a copy of the channel's TransportProfile, it will not be
     * updated if the channel's TransportProfile changes. Likewise, changing
     * it will not affect the channel (unless Channel.setTransportProfile() 
     * is called).  null means unknown or unspecified.
     * @return a copy of the channel's TransportProfile
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    TransportProfile getTransportProfile() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets a copy of the channel's data start time. This date need not be set 
     * and if set is only advisory. Data transmission may start at any time at 
     * the discretion of the channel's sender(s).
     * Because the Date object returned is a copy of the channel's data start 
     * time, it will not be updated if the channel's data start time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setDataStartTime() is called).
     * null means unknown or unspecified.
     * @return the data start time (null means none has been set)
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Date getDataStartTime() throws RMException, java.rmi.RemoteException;

    /**
     * Gets a copy of the channel's data end time. This date need not be set 
     * and if set is only advisory.  Data transmission may end at any time 
     * at the discretion of the channel's sender(s).
     * Because the Date object returned is a copy of the channel's data end 
     * time, it will not be updated if the channel's data end time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setDataEndTime() is called).
     * null means unknown or unspecified.
     * @return the data end time (null means none has been set)
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Date getDataEndTime() throws RMException, java.rmi.RemoteException;

    /**
     * Gets a copy of the channel's session end time. This is a time by which 
     * the multicast transport session associated with a channel is expected 
     * to end.  This date need not be set and if set is only advisory.
     * Because the Date object returned is a copy of the channel's session end 
     * time, it will not be updated if the channel's session end time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setSessionEndTime() is called).
     * null means unknown or unspecified.
     * @return the session end time (null means none has been set)
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Date getSessionEndTime() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the channel's suggested registration lead time. This is the time 
     * before the data start time at which receivers may begin to register.
     * This value need not be set and if set is only advisory.
     * 
     * @return lead time in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getLeadTime() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the channel's suggested registration randomizer interval. This 
     * value specifies the period of time after the registration lead time over 
     * which registrations should be spread in order to spread out channel 
     * registration activity.
     * This value need not be set and if set is only advisory.
     * 
     * @return randomizer interval in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getLeadTimeRandomInterval() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets a copy of the channel's expiration time. This is a time at which 
     * these channel characteristics expire. The receiver should have 
     * reregistered before this time and begun using the new characteristics 
     * received during reregistration.
     * This date need not be set and if set is only advisory.
     * Because the Date object returned is a copy of the channel's data end 
     * time, it will not be updated if the channel's data end time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setExpirationTime() is called).
     * null means unknown or unspecified.
     * 
     * @return a copy of the channel expiration time
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Date getExpirationTime() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the minimum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually drop below this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @return the minimum speed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getMinimumSpeed() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the maximum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually rise above this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @return the minimum speed
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getMaximumSpeed() throws RMException, java.rmi.RemoteException;

    /**
     * Gets an optional field for additional channel-specific data to be 
     * included in advertisements.
     * null means unknown or unspecified.
     * @return the additional advertised data
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String getAdditionalAdvertisedData() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets an optional field for additional channel-specific data to be 
     * included in the channel, but not included in advertisements.
     * null means unknown or unspecified.
     * @return the additional unadvertised data
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String getAdditionalUnadvertisedData() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets an optional field containing an abstract describing the channel.
     * null means unknown or unspecified.
     * @return the abstract
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String getAbstract() throws RMException, java.rmi.RemoteException;

    /**
     * Gets an optional field containing a contact name for the channel.
     * null means unknown or unspecified.
     * @return the contact name
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    String getContactName() throws RMException, java.rmi.RemoteException;

    /**
     * Tests if channel advertising has been requested.
     * @return <code>true</code> if channel advertising has been requested;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean getAdvertisingRequested() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Returns the multicast address used for advertisements.
     * The value null means use the default advertisement address.
     * @return the multicast address used for advertisements
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    InetAddress getAdvertisementAddress() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Tests if multiple senders are allowed on this channel.
     * @return <code>true</code> if multiple senders are allowed on 
     * this channel;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean isMultipleSendersAllowed() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Returns a deep copy of the channel's list of dynamic filters. Because
     * the list returned is a copy of the channel's list, it will not be
     * updated if the channel's list changes. Likewise, changing the list or
     * changing the dynamic filters in the list will not affect the channel
     * (unless Channel.setDynamicFilters() is called).
     * null means none.
     * @return a copy of the channel's dynamic filter list
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Vector getDynamicFilterList() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Tests whether the channel is enabled. Disabled channels are not 
     * accessible to anyone without administrator access. Enabled channels must 
     * be valid (as described under the validate method).
     * @return <code>true</code> if the channel is enabled;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean isEnabled() throws RMException, java.rmi.RemoteException;

    /**
     * Tests whether the channel is valid. A channel is valid if, as far as can 
     * be determined, the channel could be used to send and receive data 
     * (subject to access control restrictions).
     * Possible causes for invalid channels include not providing a transport 
     * profile or requesting support for multiple senders and providing a 
     * transport profile that cannot support this.
     * @return <code>true</code> if the channel is valid;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean isValid() throws RMException, java.rmi.RemoteException;

    /**
     * Tests if the channel is being advertised.
     * @return <code>true</code> if the channel is being advertised;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    boolean isAdvertising() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the channel ID (a long that identifies the channel uniquely,
     * at least within the ChannelManager).
     * @return the channel ID
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    long getChannelID() throws RMException, java.rmi.RemoteException;

    /**
     * Gets the count of times the channel has been advertised.
     * @return the count of times the channel has been advertised
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    long getAdvertisementCount() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets the number of seconds between the last two advertisements.
     * @return the number of seconds between the last two advertisements
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getCurrentAdvertisementInterval() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets the time the channel was last advertised.
     * @return the time the channel was last advertised.
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Date getAdvertisementTimestamp() 
            throws RMException, java.rmi.RemoteException;

    /**
     * Gets the time the channel was created.
     * @return the time the channel was created
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    Date getCreationTime() throws RMException, java.rmi.RemoteException;

    /**
     * Test method to check if the cipher functionality is being used.
     * @return <code>true</code> if the channel is being advertised;
     * <code>false</code> otherwise
     * 
     */
    boolean isUsingCipher();

    /**
     * Enables the use of cipher functionality.
     * 
     */
    void enableCipher();

    /**
     * Disables the use of cipher functionality.
     * 
     */
    void disableCipher();

    /**
     * gets the name of the specification file that is to be used to initialize 
     * the Cipher.
     * 
     * @return Name of the filename used to initialize Cipher module.
     */
    String getCipherSpecFileName();

    /**
     * sets the name of the specification file that is to be used to initialize 
     * the Cipher.
     * 
     * @param cipherSpecFileName - the Name of the filename used to initialize 
     * Cipher module.
     */
    void setCipherSpecFileName(String cipherSpecFileName);

    /**
     * Gets the number of receivers connected to the transport.
     * 
     * @return the number of receivers connected to the transport
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     * @exception java.rmi.RemoteException if an RMI-related exception occurs
     */
    int getTransportReceiverCount() throws RMException, 
	java.rmi.RemoteException, UnsupportedException;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * channel name has changed.
     */
    static final int CHANNEL_FIELD_CHANNEL_NAME = 1;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * application name has changed.
     */
    static final int CHANNEL_FIELD_APPLICATION_NAME = 2;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * transport profile has changed.
     */
    static final int CHANNEL_FIELD_TRANSPORT_PROFILE = 3;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * data start time has changed.
     */
    static final int CHANNEL_FIELD_DATA_START_TIME = 4;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * data end time has changed.
     */
    static final int CHANNEL_FIELD_DATA_END_TIME = 5;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * session end time has changed.
     */
    static final int CHANNEL_FIELD_SESSION_END_TIME = 6;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * minimum speed has changed.
     */
    static final int CHANNEL_FIELD_MINIMUM_SPEED = 7;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * maximum speed has changed.
     */
    static final int CHANNEL_FIELD_MAXIMUM_SPEED = 8;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * additional advertised data has changed.
     */
    static final int CHANNEL_FIELD_ADDITIONAL_ADVERTISED_DATA = 9;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * additional unadvertised data has changed.
     */
    static final int CHANNEL_FIELD_ADDITIONAL_UNADVERTISED_DATA = 10;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * abstract has changed.
     */
    static final int CHANNEL_FIELD_ABSTRACT = 11;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * contact name has changed.
     */
    static final int CHANNEL_FIELD_CONTACT_NAME = 12;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * advertising requested flag has changed.
     */
    static final int CHANNEL_FIELD_ADVERTISING_REQUESTED = 13;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * advertisement address has changed.
     */
    static final int CHANNEL_FIELD_ADVERTISEMENT_ADDRESS = 14;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * multiple senders allowed flag has changed.
     */
    static final int CHANNEL_FIELD_MULTIPLE_SENDERS_ALLOWED = 15;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * enabled flag has changed.
     */
    static final int CHANNEL_FIELD_ENABLED = 16;

    /**
     * A constant used in a ChannelChangedEvent to indicate that the
     * enabled flag has changed.
     */
    static final int CHANNEL_FIELD_CIPHER_MODE = 17;

// @@@ Should add more constants for the new fields that have been added.

}
