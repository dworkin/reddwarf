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
 * Localchannel.java
 */
package com.sun.multicast.reliable.channel;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.Vector;
import com.sun.multicast.util.ImpossibleException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.util.BASE64Encoder;
import com.sun.multicast.util.Util;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.advertising.Advertiser;
import com.sun.multicast.advertising.Advertisement;
import java.security.*;







/**
 * An implementation of <code>Channel</code> for the <code>LocalPCM</code>. 
 * This class is only created through the <code>LocalPCM</code> class
 * and accessed through the methods in the <code>Channel</code> interface.
 * @see                         Channel
 * @see                         LocalPCM
 */
class LocalChannel implements Channel, Cloneable, Serializable {

    /**
     * Creates a new <code>LocalChannel</code>. This constructor is not public 
     * because <code>LocalChannels</code> should only be created by 
     * <code>LocalPCMs</code>.  To create a <code>LocalChannel</code> call 
     * <code>LocalPCM.createChannel()</code>.
     * @param parentPCM the <code>LocalPCM</code> that owns this channel 
     * (may be null)
     * @param theChannelID the channel ID that the channel should have
     */
    LocalChannel(LocalPCM parentPCM, long theChannelID) {
        myPCM = parentPCM;
        channelResources = myPCM.getResources();
        channelID = theChannelID;
        channelIDObject = new Long(channelID);
    }

    /**
     * Gets the channel's ResourceBundle.
     * @return the channel's resourceBundle
     */
    ResourceBundle getChannelResources() {
        return (channelResources);
    }

    /**
     * Gets a RemoteChannel that provides access to this LocalChannel.
     * This RemoteChannel may be shared by multiple clients, as long as
     * they have the same access.
     * 
     * @return a RemoteChannel that provides access to this LocalChannel
     * @exception RemoteException if an RMI-related exception occurs

    // @@@ Access control needs to be added.

    RemoteChannel getRemoteChannel() throws RemoteException {
        if (remoteChannel == null) {
            remoteChannel = new RemoteChannel(this, 
                                              ControlledChannel.ACCESS_FULL);
        } 

        return (remoteChannel);
    }
    */

    /**
     * Clone the Channel. You should really use duplicate instead, as clone 
     * won't work on a remote object.
     * @return the newly cloned object
     * @exception java.lang.CloneNotSupportedException if a problem occurs
     */
    public synchronized Object clone() throws CloneNotSupportedException {
        Object dup;

        try {
            dup = duplicate();
        } catch (RMException e) {
            throw new CloneNotSupportedException();
        }

        return (dup);
    }

    /**
     * Destroys the Channel.  This causes all ChannelManagers to forget about 
     * the Channel.
     * The transport should not be active when destroy is called.
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void destroy() throws UnauthorizedUserException {

	// Should be caught by SAPChannel, but just in case.
        if (myPCM == null) {      
            throw new UnauthorizedUserException();
        } 

        setAdvertisingRequested(false);     // Stop advertising

        // @@@ Perhaps we should keep some memory of this channel around so 
        // that people who ask about it later can be told that it has been 
	// destroyed.

        myPCM.removeChannel(this);
    }

    /**
     * Duplicates the <code>Channel</code>. Creates a new channel exactly like 
     * this one, but with a new channel ID.
     * @return the new Channel
     * @exception com.sun.multicast.reliable.channel.LimitExceededException if 
     * the PCM's channel limit has been reached
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized Channel duplicate() 
            throws LimitExceededException, UnauthorizedUserException {

	// Should be caught by ControlledChannel, but just in case.
        if (myPCM == null) {      
            throw new UnauthorizedUserException();
        } 

        return (myPCM.duplicateChannel(this));
    }

    /**
     * Do the LocalChannel part of duplicating a <code>Channel</code>.
     * Creates a new channel exactly like this one, but with a new channel ID.
     * Does not take care of the PCM side of things.
     * 
     * <STRONG>Note:</STRONG> This is a package-local method. It should only be
     * called by LocalPCM.duplicateChannel().
     * @param theChannelID the channel ID that the channel should have
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    LocalChannel dupYourself(long theChannelID) 
            throws UnauthorizedUserException {
        LocalChannel dup;

        try {
            dup = (LocalChannel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new NullPointerException();
        }

        dup.channelID = theChannelID;
        dup.channelIDObject = new Long(theChannelID);

        if (tProfile != null) {
            dup.tProfile = (TransportProfile) tProfile.clone();
        } 

        dup.cipherMode = cipherMode;
        dup.cipherSpecFileName = new String(cipherSpecFileName);

        if (dataStartTime != null) {
            dup.dataStartTime = new Date(dataStartTime.getTime());
        } 
        if (dataEndTime != null) {
            dup.dataEndTime = new Date(dataEndTime.getTime());
        } 
        if (sessionEndTime != null) {
            dup.sessionEndTime = new Date(sessionEndTime.getTime());
        } 
        if (dataStartTime != null) {
            dup.dataStartTime = new Date(dataStartTime.getTime());
        } 

        dup.creationTime = new Date();

        if (advertising) {
            dup.advertising = false;
            dup.advertisingRequested = false;
            dup.ourAd = null;

            dup.setAdvertisingRequested(true);
        }

        return (dup);
    }

    /**
     * Returns a brief text description of the channel.
     */
    public String toString() {
        String value = "Channel ID " + Long.toString(channelID);

        if (channelName != null) {
            value = value + " (named '" + channelName + "')";
        } 

        return (value);
    }

    /**
     * Throws an exception if the channel is not valid. A channel is valid if, 
     * as far as can be determined, the channel could be used to send and 
     * receive data (subject to access control restrictions).
     * 
     * <P> Possible causes for invalid channels  not providing a 
     * transport profile, providing an invalid transport profile, or requesting 
     * support for multiple senders and providing a transport profile that 
     * cannot support this.
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is not valid
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void validate() 
            throws InvalidChannelException, UnauthorizedUserException {
        if (tProfile == null) {
            throw new InvalidChannelException(channelResources.getString(
		"NoTransportProfile"));
        } 
        if (!tProfile.isValid()) {
            throw new InvalidChannelException(channelResources.getString(
		"InvalidTransportProfile"));
        } 
        if (multipleSendersAllowed &&!tProfile.isMultiSender()) {
            throw new InvalidChannelException(channelResources.getString(
		"InvalChanMultSenders"));
        } 
    }

    /**
     * Creates an RMStreamSocket for sending and/or receiving on the channel.
     * 
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new RMStreamSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     */
    public ChannelRMStreamSocket createRMStreamSocket(int sendReceive) 
            throws RMException, UnsupportedException, IOException {
        return new ChannelRMStreamSocket(tProfile, sendReceive);
    }

    /**
     * Creates an RMStreamSocket for sending and/or receiving on the channel.
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
     * @return a new RMStreamSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     */
    public ChannelRMStreamSocket createRMStreamSocket(TransportProfile tp, 
            int sendReceive) throws RMException, UnsupportedException, 
                                    IOException {
        return new ChannelRMStreamSocket(tp, sendReceive);
    }

    /**
     * Creates an RMPacketSocket for sending and/or receiving on the channel.
     * 
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new RMPacketSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     */
    public ChannelRMPacketSocket createRMPacketSocket(int sendReceive) 
            throws RMException, UnsupportedException, IOException {

        ChannelRMPacketSocket rmSocket = new ChannelRMPacketSocket(tProfile, 
                dynamicFilters, sendReceive);



cipherMode = false;

        return rmSocket;
    }

    /**
     * Creates an RMPacketSocket for sending and/or receiving on the channel.
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
     * @return a new RMPacketSocket
     * @exception com.sun.multicast.reliable.RMException if a
     * reliable-multicast-related exception occurs
     */
    public ChannelRMPacketSocket createRMPacketSocket(TransportProfile tp, 
            int sendReceive) throws RMException, UnsupportedException, 
                                    IOException {
        return new ChannelRMPacketSocket(tp, dynamicFilters, sendReceive);
    }

    /**
     * Add a ChannelChangeListener to the listener list.
     * @param listener the listener to be added
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void addChannelChangeListener(
	ChannelChangeListener listener) throws UnauthorizedUserException {

        if (listeners == null) {
            listeners = new Vector();
        } 
        if (!listeners.contains(listener)) {
            listeners.addElement(listener);
        } 
    }

    /**
     * Remove a ChannelChangeListener from the listener list.
     * @param listener the listener to be removed
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void removeChannelChangeListener(
	ChannelChangeListener listener) throws UnauthorizedUserException {

        if (listeners == null) {
            return;
        } 

        listeners.removeElement(listener);
    }

    /**
     * Sets the channel name.
     * null means unknown or unspecified. For maximum compatibility with SAP, 
     * this name should not contain LF or CR (U+000A or U+000D).
     * @param name the new channel name
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setChannelName(String name) throws UnauthorizedUserException {
        if (name == null) {
            if (channelName == null) {
                return;
            } 
        } else if (name.equals(channelName)) {
            return;
        } 

        channelName = name;

        changed(CHANNEL_FIELD_CHANNEL_NAME);
    }

    /**
     * Sets the channel's application name.
     * null means unknown or unspecified. For maximum compatibility with SAP, 
     * this name should not contain LF, CR, or space 
     * (U+000A, U+000D, or U+0020).
     * @param name the new application name
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setApplicationName(String name) 
            throws UnauthorizedUserException {
        if (name == null) {
            if (applicationName == null) {
                return;
            } 
        } else if (name.equals(applicationName)) {
            return;
        } 

        applicationName = name;

        changed(CHANNEL_FIELD_APPLICATION_NAME);
    }

    /**
     * Sets the channel's transport profile. Because the Channel object may be
     * remote, the channel actually makes a copy of the TransportProfile 
     * provided and uses that.
     * null means unknown or unspecified.
     * @param profile the new transport profile
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is enabled and this would cause it to become invalid
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void setTransportProfile(TransportProfile profile) 
            throws InvalidChannelException, UnauthorizedUserException {
        if (enabled) {
            if (profile == null) {
                throw new InvalidChannelException(channelResources.getString(
		    "NoTransportProfile"));
            } 
            if (!profile.isValid()) {
                throw new InvalidChannelException(channelResources.getString(
		    "InvalidTransportProfile"));
            } 
            if (multipleSendersAllowed &&!profile.isMultiSender()) {
                throw new InvalidChannelException(channelResources.getString(
		    "InvalChanMultSenders"));
            } 
        }
        if (profile == null) {
            tProfile = null;
        } else {
            tProfile = (TransportProfile) profile.clone();
        }

        tProfileChanged = true;

        changed(CHANNEL_FIELD_TRANSPORT_PROFILE);
    }

    /**
     * Sets the channel's data start time. This date need not be set and if set 
     * is only advisory.  Data transmission may start at any time at the 
     * discretion of the channel's sender(s).
     * null means unknown or unspecified.
     * Because the channel may be remote, the channel actually makes a copy 
     * of the Date provided and uses that.
     * @param startTime the new data start time (null, the default, means 
     * unknown)
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setDataStartTime(Date startTime) 
            throws UnauthorizedUserException {
        if (startTime == null) {
            if (dataStartTime == null) {
                return;
            } 

            dataStartTime = null;
        } else {
            if (startTime.equals(dataStartTime)) {
                return;
            } 

            dataStartTime = new Date(startTime.getTime());
        }

        changed(CHANNEL_FIELD_DATA_START_TIME);
    }

    /**
     * Sets the channel's data end time. This date need not be set and if set 
     * is only advisory.  Data transmission may end at any time at the 
     * discretion of the channel's sender(s).
     * null means unknown or unspecified.
     * Because the channel may be remote, the channel actually makes a copy 
     * of the Date provided and uses that.
     * @param endTime the new data end time (null, the default, means unknown)
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current * principal does not have authorization to perform 
     * this action
     */
    public void setDataEndTime(Date endTime) 
            throws UnauthorizedUserException {
        if (endTime == null) {
            if (dataEndTime == null) {
                return;
            } 

            dataEndTime = null;
        } else {
            if (endTime.equals(dataEndTime)) {
                return;
            } 

            dataEndTime = new Date(endTime.getTime());
        }

        changed(CHANNEL_FIELD_DATA_END_TIME);
    }

    /**
     * Sets the channel's session end time. This is a time by which the 
     * multicast transport session associated with a channel is expected to end.
     * This date need not be set and if set is only advisory.
     * null means unknown or unspecified.
     * Because the channel may be remote, the channel actually makes a copy of 
     * the Date provided and uses that.
     * @param endTime the new session end time (null, the default, 
     * means unknown)
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setSessionEndTime(Date endTime) 
            throws UnauthorizedUserException {
        if (endTime == null) {
            if (sessionEndTime == null) {
                return;
            } 

            sessionEndTime = null;
        } else {
            if (endTime.equals(sessionEndTime)) {
                return;
            } 

            sessionEndTime = new Date(endTime.getTime());
        }

        changed(CHANNEL_FIELD_SESSION_END_TIME);
    }

    /**
     * Sets the channel's suggested registration lead time. This is the time 
     * before the data start time at which receivers may begin to register.
     * This value need not be set and if set is only advisory.
     * 
     * @param leadTime lead time in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public void setLeadTime(int leadTime) throws RMException {
        this.leadTime = leadTime;
    }

    /**
     * Sets the channel's suggested registration randomizer interval. 
     * This value specifies the period of time after the registration lead time 
     * over which registrations should be spread in order to spread out channel 
     * registration activity.  This value need not be set and if set is only 
     * advisory.
     * 
     * @param interval randomizer interval in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public void setLeadTimeRandomInterval(int interval) throws RMException {
        this.leadTimeRandom = interval;
    }

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
     */
    public void setExpirationTime(Date expirationTime) throws RMException {
        this.expirationTime = expirationTime;
    }

    /**
     * Sets the minimum speed at which the channel sends data 
     * in bits per second.  This speed need not be set and if set is only 
     * advisory. 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually drop below this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @param speed the new minimum speed
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setMinimumSpeed(int speed) throws UnauthorizedUserException {
        if (speed < 0) {
            throw new IllegalArgumentException();
        } 

        minimumSpeed = speed;

        changed(CHANNEL_FIELD_MINIMUM_SPEED);
    }

    /**
     * Sets the maximum speed at which the channel sends data in 
     * bits per second.  This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually rise above this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @param speed the new minimum speed
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setMaximumSpeed(int speed) throws UnauthorizedUserException {
        if (speed < 0) {
            throw new IllegalArgumentException();
        } 

        maximumSpeed = speed;

        changed(CHANNEL_FIELD_MAXIMUM_SPEED);
    }

    /**
     * Sets an optional field for additional channel-specific data to be 
     * included in advertisements. As much of this field as possible is 
     * included in SAP advertisements, up to about 600 bytes.
     * null means unknown or unspecified.
     * @param data the new additional advertised data (null means unknown)
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setAdditionalAdvertisedData(String data) 
            throws UnauthorizedUserException {
        if (data == null) {
            if (additionalAdvertisedData == null) {
                return;
            } 
        } else if (data.equals(additionalAdvertisedData)) {
            return;
        } 

        additionalAdvertisedData = data;

        changed(CHANNEL_FIELD_ADDITIONAL_ADVERTISED_DATA);
    }

    /**
     * Sets an optional field for additional channel-specific data to be 
     * included in the channel, but not included in advertisements.
     * null means unknown or unspecified.
     * @param data the new additional unadvertised data (null means unknown)
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setAdditionalUnadvertisedData(String data) 
            throws UnauthorizedUserException {
        additionalUnadvertisedData = data;

        changed(CHANNEL_FIELD_ADDITIONAL_UNADVERTISED_DATA);
    }

    /**
     * Sets an optional field containing an abstract describing the channel. 
     * As much of this field as possible is included in SAP advertisements, 
     * up to about 600 bytes.
     * null means unknown or unspecified. For maximum compatibility with SAP, 
     * this string should not contain LF or CR (U+000A or U+000D).
     * @param data the new abstract
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setAbstract(String data) throws UnauthorizedUserException {
        if (data == null) {
            if (abstractString == null) {
                return;
            } 
        } else if (data.equals(abstractString)) {
            return;
        } 

        abstractString = data;

        changed(CHANNEL_FIELD_ABSTRACT);
    }

    /**
     * Sets an optional field containing a contact name for the channel.
     * null means unknown or unspecified. For maximum compatibility with SAP, 
     * this name should be an email address not containing LF or CR 
     * (U+000A or U+000D).
     * @param data the new contact name
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public void setContactName(String data) throws UnauthorizedUserException {
        if (data == null) {
            if (contactName == null) {
                return;
            } 
        } else if (data.equals(contactName)) {
            return;
        } 

        contactName = data;

        changed(CHANNEL_FIELD_CONTACT_NAME);
    }

    /**
     * Sets whether the channel is enabled. Disabled channels are not 
     * accessible to anyone without administrator access. Enabled channels 
     * must be valid (as described under the validate method).
     * @param b if <code>true</code>, enable the channel;
     * otherwise, disable it
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is invalid and enabling was requested
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void setEnabled(boolean b) 
            throws InvalidChannelException, UnauthorizedUserException {
        if (b == enabled) {
            return;
        } 
        if (b) {
            validate();
        } 

        enabled = b;

        updateAdvertisingFlag();
        changed(CHANNEL_FIELD_ENABLED);
    }

    /**
     * Sets whether channel advertising is requested.
     * 
     * <P><strong>NOTE:</strong> A channel is only advertised if it is enabled 
     * and channel advertising has been requested.
     * @param advertising <code>true</code> if the channel should be advertised;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void setAdvertisingRequested(boolean newAdvertising) 
            throws UnauthorizedUserException {
        if (newAdvertising == advertisingRequested) {
            return;
        } 

        advertisingRequested = newAdvertising;

        updateAdvertisingFlag();
        changed(CHANNEL_FIELD_ADVERTISING_REQUESTED);
    }

    /**
     * Sets the requested multicast address to be used for SAP advertisements.
     * The value null means use the default SAP advertisement address.
     * Note that the TTL used for advertisements is the same as that specified 
     * in the TransportProfile.
     * @param address the new multicast address
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     * @exception java.io.IOException if the address is not a multicast address
     */
    public synchronized void setAdvertisementAddress(InetAddress address) 
            throws UnauthorizedUserException, java.io.IOException {
        if (address == null) {
            if (advertisementAddress == null) {
                return;
            } 
        } else {
            if (address.equals(advertisementAddress)) {
                return;
            } 
            if (address.isMulticastAddress() == false) {
                throw new IOException(channelResources.getString(
		    "AdvAddrNotMCast"));
            } 
        }

        boolean wasAdvertising = advertising;

        if (wasAdvertising) {
            stopAdvertising();
        } 

        advertisementAddress = address;

        if (wasAdvertising) {
            startAdvertising();
        } 

        changed(CHANNEL_FIELD_ADVERTISEMENT_ADDRESS);
    }

    /**
     * Sets if multiple senders are allowed on this channel.
     * @param multiple <code>true</code> if multiple senders should be allowed 
     * on this channel;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.InvalidChannelException 
     * if the channel is enabled and this would cause it to become invalid
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void setMultipleSendersAllowed(boolean multiple) 
            throws InvalidChannelException, UnauthorizedUserException {
        if (enabled && multiple && (tProfile.isMultiSender() == false)) {
            throw new InvalidChannelException(channelResources.getString(
		"InvalChanMultSenders"));
        } 

        multipleSendersAllowed = multiple;

        changed(CHANNEL_FIELD_MULTIPLE_SENDERS_ALLOWED);
    }

    /**
     * Sets the channel's dynamic filter list. Because the Channel object may be
     * remote, the channel actually makes a deep copy of the list provided
     * and uses that. null means none.
     * @param list the new dynamic filter list
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized void setDynamicFilterList(Vector list) 
            throws UnauthorizedUserException {
        if ((list == null) || (list.size() == 0)) {
            dynamicFilters = null;
        } else {

            // @@@ Should note that these aren't distributed via SAP. Maybe
            // prevent you from advertising such a channel via SAP?
            // Use object serialization to make a deep copy.

            Vector newList = (Vector) Util.deepClone(list);

            // Check that all elements are DynamicFilters.

            for (Enumeration e = newList.elements(); e.hasMoreElements(); ) {
                if (!(e.nextElement() instanceof DynamicFilter)) {
                    throw new IllegalArgumentException(
			"list must contain only DynamicFilters");
                } 
            }

            dynamicFilters = newList;
        }
    }

    /**
     * Start advertising this channel using SAP.
     */
    synchronized void startAdvertising() {
        try {
            if (advertiser == null) {
                advertiser = Advertiser.getAdvertiser();
            } 

            ourAd = new Advertisement(tProfile.getAddress(), 
                                      tProfile.getTTL());

            if (channelName != null) {
                updateAdvertising(CHANNEL_FIELD_CHANNEL_NAME);
            } 
            if (applicationName != null) {
                updateAdvertising(CHANNEL_FIELD_APPLICATION_NAME);
            } 
            if (dataStartTime != null) {
                updateAdvertising(CHANNEL_FIELD_DATA_START_TIME);
            } 
            if (sessionEndTime != null) {
                updateAdvertising(CHANNEL_FIELD_SESSION_END_TIME);
            } 
            if (abstractString != null) {
                updateAdvertising(CHANNEL_FIELD_ABSTRACT);
            } 
            if (contactName != null) {
                updateAdvertising(CHANNEL_FIELD_CONTACT_NAME);
            } 
            if (additionalAdvertisedData != null) {
                updateAdvertising(CHANNEL_FIELD_ADDITIONAL_ADVERTISED_DATA);
            } 

            // tProfile must be != null or the channel wouldn't be enabled

            updateAdvertising(CHANNEL_FIELD_TRANSPORT_PROFILE);
            updateAdvertising(CHANNEL_FIELD_CIPHER_MODE);

            if (advertisementAddress == null) {
                advertiser.startAdvertising(ourAd);
            } else {
                advertiser.startAdvertising(advertisementAddress, ourAd);
            }

            advertising = true;
        } catch (UnknownHostException e) {
            throw new NullPointerException();
        } catch (IOException e) {
            throw new NullPointerException();
        }
    }

    /**
     * Stop advertising this channel using SAP.
     */
    synchronized void stopAdvertising() {
        advertiser.stopAdvertising(ourAd);

        ourAd = null;
        advertising = false;
        setChannelIDInAd = false;
        tProfileAttr = null;
        cipherModeAttr = null;


    }

    /**
     * Strip all occurences of a bad character out of a string. Accepts null.
     * @param in string to be stripped
     * @param badChar bad character
     * @return stripped string
     */
    String stripString(String in, char badChar) {
        if (in == null) {
            return (in);
        } 

        StringBuffer out = new StringBuffer(in.length());
        int next = 0;
        int indexVal = in.indexOf(badChar);

        while (indexVal != -1) {
            if (indexVal > next) {
                out.append(in.substring(next, indexVal));
            } 

            next = indexVal + 1;
            indexVal = in.indexOf(badChar, next);
        }

        return (out.toString());
    }

    /**
     * Strip all occurences of bad characters out of a string. Accepts null.
     * @param in string to be stripped
     * @param badChars string of bad characters
     * @return stripped string
     */
    String stripString(String in, String badChars) {
        if ((in == null) || (badChars == null)) {
            return (in);
        } 

        String out = in;

        for (int i = badChars.length() - 1; i >= 0; i--) {
            if (out.indexOf(badChars.charAt(i)) != -1) {
                out = stripString(out, badChars.charAt(i));
            } 
        }

        return (out);
    }

    /**
     * Update this channel's SAP advertisement.
     * @param changeField one of CHANNEL_FIELD_*
     */
    synchronized void updateAdvertising(int changeField) {
        if (ourAd == null) {
            return;
        } 

        switch (changeField) {

        case CHANNEL_FIELD_CHANNEL_NAME: 

            // @@@ If the channel name contains these characters, we should 
	    // probably store the full version of it in an attribute 
	    // (BASE64 encoded).

            ourAd.setName(stripString(channelName, BAD_SDP_FIELD_CHARS));

            break;

        case CHANNEL_FIELD_APPLICATION_NAME: 
            ourAd.setOwner(stripString(applicationName, BAD_APP_NAME_CHARS));

            break;

        case CHANNEL_FIELD_TRANSPORT_PROFILE: 
            if (tProfileAttr != null) {
                ourAd.removeAttribute(tProfileAttr);

                tProfileAttr = null;
            }

            // Serialize the TransportProfile to a byte stream, 
	    // then BASE64 encode it

            try {
                String encodedString = 
                    new String(BASE64Encoder.encode(
			Util.writeObject(tProfile)), "UTF8");

                tProfileAttr = SAPChannel.TRANSPORT_PROFILE_ATTR 
                               + encodedString;

                ourAd.addAttribute(tProfileAttr);

                tProfileChanged = false;
            } catch (IOException e) {
                e.printStackTrace();
            }

            ourAd.setAdvertisedAddress(tProfile.getAddress());
            ourAd.setAdvertisedTTL(tProfile.getTTL());

            break;

        case CHANNEL_FIELD_CIPHER_MODE: 

            // System.out.println("The cipherModeFlag is " + cipherMode);

            Boolean Bool = new Boolean(cipherMode);

            cipherModeAttr = SAPChannel.CIPHER_MODE_ATTR + Bool.toString();

            ourAd.addAttribute(cipherModeAttr);

            break;

        case CHANNEL_FIELD_DATA_START_TIME: 
            ourAd.setStartTime(dataStartTime);

            break;

        case CHANNEL_FIELD_SESSION_END_TIME: 
            ourAd.setEndTime(sessionEndTime);

            break;

        case CHANNEL_FIELD_ABSTRACT: 
            ourAd.setInfo(stripString(abstractString, BAD_SDP_FIELD_CHARS));

            break;

        case CHANNEL_FIELD_CONTACT_NAME: 
            ourAd.setEMailAddress(stripString(contactName, 
                                              BAD_SDP_FIELD_CHARS));

            break;

        case CHANNEL_FIELD_ADDITIONAL_ADVERTISED_DATA: 
            if (additionalAdvertisedDataAttr != null) {
                ourAd.removeAttribute(additionalAdvertisedDataAttr);

                additionalAdvertisedDataAttr = null;
            }

            // BASE64 encode so that it can contain CRLFs

            try {
                additionalAdvertisedDataAttr = 
                    SAPChannel.ADDITIONAL_ADVERTISED_DATA_ATTR 
                    + new String(BASE64Encoder.encode(
			additionalAdvertisedData.getBytes("UTF8")), "UTF8");

                ourAd.addAttribute(additionalAdvertisedDataAttr);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            break;

        default: 
            break;
        }

        // Set channel ID (only happens once per ad, since channel ID should 
        // never change)

        if (!setChannelIDInAd) {
            ourAd.addAttribute(SAPChannel.CHANNEL_ID_ATTR 
                               + Long.toString(channelID));

            setChannelIDInAd = true;
        }
    }

    /**
     * Turn advertising on or off to reflect changes in the enabled or 
     * advertisingRequested flags.
     */
    synchronized void updateAdvertisingFlag() {
        if (advertising && (!enabled ||!advertisingRequested)) {
            stopAdvertising();
        } else if (!advertising && enabled && advertisingRequested) {
            startAdvertising();
        } 
    }

    /**
     * Change the current advertisement to reflect changes in advertised fields.
     * @param channelField the channel field being tested
     * @return <code>true</code> if the field is included in advertisements;
     * <code>false</code> otherwise
     */
    synchronized void changed(int channelField) {
        if (advertising) {
            updateAdvertising(channelField);
        } 
        if ((listeners == null) || (listeners.isEmpty())) {
            return;
        } 

        ChannelChangeEvent cce = new ChannelChangeEvent(this, channelField);
        Enumeration le = listeners.elements();

        while (le.hasMoreElements()) {
            ((ChannelChangeListener) le.nextElement()).channelChange(cce);
        }
    }

    /**
     * Returns the channel name.
     * null means unknown or unspecified.
     * @return the channel name
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public String getChannelName() throws UnauthorizedUserException {
        return (channelName);
    }

    /**
     * Returns the channel's application name.
     * null means unknown or unspecified.
     * @return the channel's application name
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public String getApplicationName() throws UnauthorizedUserException {
        return (applicationName);
    }

    /**
     * Returns a copy of the channel's TransportProfile. Because the profile
     * returned is a copy of the channel's TransportProfile, it will not be
     * updated if the channel's TransportProfile changes. Likewise, changing
     * it will not affect the channel (unless Channel.setTransportProfile() 
     * is called).
     * null means unknown or unspecified.
     * @return a copy of the channel's TransportProfile
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized TransportProfile getTransportProfile() 
            throws UnauthorizedUserException {
        if (tProfile == null) {
            return (null);
        } else {
            return ((TransportProfile) tProfile.clone());
        }
    }

    /**
     * Gets a copy of the channel's data start time. This date need not be set 
     * and if set is only advisory. Data transmission may start at any time at 
     * the discretion of the channel's sender(s).
     * Because the Date object returned is a copy of the channel's data start 
     * time, it will not be updated if the channel's data start time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setDataStartTime() is called).
     * null means unknown or unspecified.
     * @return the data start time
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public Date getDataStartTime() throws UnauthorizedUserException {
        if (dataStartTime == null) {
            return (null);
        } else {
            return (new Date(dataStartTime.getTime()));
        }
    }

    /**
     * Gets a copy of the channel's data end time. This date need not be set 
     * and if set is only advisory.  Data transmission may end at any time at 
     * the discretion of the channel's sender(s).
     * Because the Date object returned is a copy of the channel's data end 
     * time, it will not be updated if the channel's data end time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setDataEndTime() is called).
     * null means unknown or unspecified.
     * @return the data end time
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public Date getDataEndTime() throws UnauthorizedUserException {
        if (dataEndTime == null) {
            return (null);
        } else {
            return (new Date(dataEndTime.getTime()));
        }
    }

    /**
     * Gets a copy of the channel's session end time. This is a time by which 
     * the multicast transport session associated with a channel is expected 
     * to end.  This date need not be set and if set is only advisory.
     * Because the Date object returned is a copy of the channel's session end 
     * time, it will not be updated if the channel's session end time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setSessionEndTime() is called).
     * null means unknown or unspecified.
     * @return the session end time
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public Date getSessionEndTime() throws UnauthorizedUserException {
        if (sessionEndTime == null) {
            return (null);
        } else {
            return (new Date(sessionEndTime.getTime()));
        }
    }

    /**
     * Gets the channel's suggested registration lead time. This is the time 
     * before the data start time at which receivers may begin to register.
     * This value need not be set and if set is only advisory.
     * 
     * @return lead time in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public int getLeadTime() throws RMException {
        return (leadTime);
    }

    /**
     * Gets the channel's suggested registration randomizer interval. 
     * This value specifies the period of time after the registration lead time 
     * over which registrations should be spread in order to spread out channel 
     * registration activity.  This value need not be set and if set is 
     * only advisory.
     * 
     * @return randomizer interval in seconds
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public int getLeadTimeRandomInterval() throws RMException {
        return (leadTimeRandom);
    }

    /**
     * Gets a copy of the channel's expiration time. This is a time at which 
     * these channel characteristics expire. The receiver should have 
     * reregistered before this time and begun using the new characteristics 
     * received during reregistration.  This date need not be set and if set 
     * is only advisory.  Because the Date object returned is a copy of the 
     * channel's data end time, it will not be updated if the channel's data end
     * time changes. Likewise, changing it will not affect the channel 
     * (unless Channel.setExpirationTime() is called).
     * null means unknown or unspecified.
     * 
     * @return a copy of the channel expiration time
     * @exception com.sun.multicast.reliable.RMException if a 
     * reliable-multicast-related exception occurs
     */
    public Date getExpirationTime() throws RMException {
        return (expirationTime);
    }

    /**
     * Gets the minimum speed at which the channel sends data in 
     * bits per second.  This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually drop below this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @return the minimum speed
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public int getMinimumSpeed() throws UnauthorizedUserException {
        return (minimumSpeed);
    }

    /**
     * Gets the maximum speed at which the channel sends data in 
     * bits per second.  This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually rise above this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @return the minimum speed
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public int getMaximumSpeed() throws UnauthorizedUserException {
        return (maximumSpeed);
    }

    /**
     * Gets an optional field for additional channel-specific data to be 
     * included in advertisements.
     * null means unknown or unspecified.
     * @return the additional advertised data
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public String getAdditionalAdvertisedData() 
            throws UnauthorizedUserException {
        return (additionalAdvertisedData);
    }

    /**
     * Gets an optional field for additional channel-specific data to be 
     * included in the channel, but not included in advertisements.
     * null means unknown or unspecified.
     * @return the additional unadvertised data
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public String getAdditionalUnadvertisedData() 
            throws UnauthorizedUserException {
        return (additionalUnadvertisedData);
    }

    /**
     * Gets an optional field containing an abstract describing the channel.
     * null means unknown or unspecified.
     * @return the abstract
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public String getAbstract() throws UnauthorizedUserException {
        return (abstractString);
    }

    /**
     * Gets an optional field containing a contact name for the channel.
     * null means unknown or unspecified.
     * @return the contact name
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public String getContactName() throws UnauthorizedUserException {
        return (contactName);
    }

    /**
     * Tests if channel advertising has been requested.
     * @return <code>true</code> if channel advertising has been requested;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public boolean getAdvertisingRequested() 
            throws UnauthorizedUserException {
        return (advertisingRequested);
    }

    /**
     * Returns the multicast address used for SAP advertisements.
     * The value null means use the default SAP advertisement address.
     * @return the multicast address used for SAP advertisements
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public InetAddress getAdvertisementAddress() 
            throws UnauthorizedUserException {
        return (advertisementAddress);
    }

    /**
     * Tests if multiple senders are allowed on this channel.
     * @return <code>true</code> if multiple senders are allowed on 
     * this channel; <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public boolean isMultipleSendersAllowed() 
            throws UnauthorizedUserException {
        return (multipleSendersAllowed);
    }

    /**
     * Returns a deep copy of the channel's list of dynamic filters. Because
     * the list returned is a copy of the channel's list, it will not be
     * updated if the channel's list changes. Likewise, changing the list or
     * changing the dynamic filters in the list will not affect the channel
     * (unless Channel.setDynamicFilters() is called).
     * null means none.
     * @return a copy of the channel's dynamic filter list
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public synchronized Vector getDynamicFilterList() 
            throws UnauthorizedUserException {
        if (dynamicFilters == null) {
            return (null);
        } else {
            return ((Vector) Util.deepClone(dynamicFilters));
        }
    }

    /**
     * Tests whether the channel is enabled. Disabled channels are not 
     * accessible to anyone without administrator access. Enabled channels 
     * must be valid (as described under the validate method).
     * @return <code>true</code> if the channel is enabled;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public boolean isEnabled() throws UnauthorizedUserException {
        return (enabled);
    }

    /**
     * Tests whether the channel is valid. A channel is valid if, as far as 
     * can be determined, the channel could be used to send and receive data 
     * (subject to access control restrictions).
     * 
     * <P> Possible causes for invalid channels  not providing a 
     * transport profile, providing an invalid transport profile, or 
     * requesting support for multiple senders and providing a 
     * transport profile that cannot support this.
     * @return <code>true</code> if the channel is valid;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public boolean isValid() throws UnauthorizedUserException {
        boolean valid = true;

        try {
            validate();
        } catch (InvalidChannelException e) {
            valid = false;
        }

        return (valid);
    }

    /**
     * Tests if the channel is being advertised.
     * 
     * <P><strong>NOTE:</strong> A channel is only advertised if it is enabled 
     * and channel advertising has been requested.
     * @return <code>true</code> if the channel is being advertised;
     * <code>false</code> otherwise
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public boolean isAdvertising() throws UnauthorizedUserException {
        return (advertising);
    }

    /**
     * Gets the channel ID (a long that identifies the channel uniquely,
     * at least within the ChannelManager).
     * @return the channel ID
     */

    // Needn't be synchronized unless the channel ID can change.

    public long getChannelID() {
        return (channelID);
    }

    /**
     * Gets a Long object containing the channel ID.
     * 
     * <P>This method isn't public. It's just an optimization used by 
     * the LocalPCM to avoid creating a new Long object whenever it wants to 
     * put a channel ID into a Hashtable or Vector.
     * @return a Long object containing the channel ID
     */
    Long getChannelIDObject() {
        return (channelIDObject);
    }

    /**
     * Gets the count of times the channel has been advertised using SAP.
     * @return the count of times the channel has been advertised
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     * @exception com.sun.multicast.reliable.channel.NotAdvertisingException 
     * if the channel is not advertising
     */
    public synchronized long getAdvertisementCount() 
            throws UnauthorizedUserException, NotAdvertisingException {
        if (!advertising) {
            throw new NotAdvertisingException();
        } 

        return (ourAd.getAdvertisementCount());
    }

    /**
     * Gets the number of seconds between the last two SAP advertisements.
     * @return the number of seconds between the last two SAP advertisements
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     * @exception com.sun.multicast.reliable.channel.NotAdvertisingException 
     * if the channel is not advertising
     */
    public synchronized int getCurrentAdvertisementInterval() 
            throws UnauthorizedUserException, NotAdvertisingException {
        if (!advertising) {
            throw new NotAdvertisingException();
        } 

        return (ourAd.getCurrentAdvertisementInterval());
    }

    /**
     * Gets the time the channel was last advertised via SAP.
     * @return the time the channel was last advertised via SAP
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     * @exception com.sun.multicast.reliable.channel.NotAdvertisingException 
     * if the channel is not advertising
     */
    public synchronized Date getAdvertisementTimestamp() 
            throws UnauthorizedUserException, NotAdvertisingException {
        if (!advertising) {
            throw new NotAdvertisingException();
        } 

        return (ourAd.getAdvertisementTimestamp());
    }

    /**
     * Gets the time the channel was created.
     * @return the time the channel was created
     * @exception com.sun.multicast.reliable.channel.UnauthorizedUserException 
     * if the current principal does not have authorization to perform 
     * this action
     */
    public Date getCreationTime() throws UnauthorizedUserException {
        return (new Date(creationTime.getTime()));
    }

    /**
     * Test method to check if the cipher functionality is being used.
     * @return <code>true</code> if the channel is being advertised;
     * <code>false</code> otherwise
     * 
     */
    public boolean isUsingCipher() {
        return cipherMode;
    }

    /**
     * Enables the use of cipher functionality.
     * 
     */
    public void enableCipher() {
        if (cipherMode != true) {
            cipherMode = true;

            changed(CHANNEL_FIELD_CIPHER_MODE);
        }
    }



    /**
     * Disables the use of cipher functionality.
     * 
     */
    public void disableCipher() {
        if (cipherMode != false) {
            cipherMode = false;

            changed(CHANNEL_FIELD_CIPHER_MODE);
        }
    }


    /**
     * gets the name of the specification file that is to be used to 
     * initialize the Cipher.
     * 
     * @return Name of the filename used to initialize Cipher module.
     */
    public String getCipherSpecFileName() {
        return cipherSpecFileName;
    }


    /**
     * sets the name of the specification file that is to be used to 
     * initialize the Cipher.
     * 
     * @param cipherSpecFileName - the Name of the filename used to 
     * initialize Cipher module.
     */
    public void setCipherSpecFileName(String cipherSpecFileName) {
        this.cipherSpecFileName = cipherSpecFileName;
    }

    /**
     * Gets the number of receivers connected to the transport.
     * 
     * @return the number of receivers connected to the transport
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public int getTransportReceiverCount() 
            throws RMException, RemoteException, UnsupportedException {
	throw new UnsupportedException();
    }


    private String channelName;
    private String applicationName;
    private TransportProfile tProfile;
    private Date dataStartTime;
    private Date dataEndTime;
    private Date sessionEndTime;
    private int minimumSpeed;
    private int maximumSpeed;
    private String additionalAdvertisedData;
    private String additionalUnadvertisedData;
    private String abstractString;
    private String contactName;
    private InetAddress advertisementAddress;
    private boolean enabled = false;
    private boolean multipleSendersAllowed = false;
    private long channelID;
    private Long channelIDObject;
    private boolean advertising = false;
    private boolean advertisingRequested = false;
    private boolean setChannelIDInAd = false;
    private String tProfileAttr = null;
    private String cipherModeAttr = null;
    private String additionalAdvertisedDataAttr = null;
    private boolean tProfileChanged = false;
    private Date creationTime = new Date();
    private transient LocalPCM myPCM;
    private transient ResourceBundle channelResources;
    private Vector dynamicFilters = null;
    private static Advertiser advertiser;
    private transient Advertisement ourAd;
    private int leadTime;
    private int leadTimeRandom;
    private Date expirationTime;
    private boolean cipherMode = false;
    private String cipherSpecFileName = null;
    private Vector listeners;       // Vector of ChannelChangeListeners
    private static final String BAD_SDP_FIELD_CHARS = 
        "\n\r";                     // No CR or LF in SDP fields
    private static final String BAD_APP_NAME_CHARS = 
        "\n\r ";                    // No CR, LF, or space in

    // SDP application name

}
