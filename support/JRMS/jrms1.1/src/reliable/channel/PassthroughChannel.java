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
 * PassthroughChannel.java
 */
package com.sun.multicast.reliable.channel;

import java.io.IOException;
import java.rmi.RemoteException;
import java.net.InetAddress;
import java.util.Date;
import java.util.Vector;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;

/**
 * A channel that simply passes through all calls to another channel. This
 * class is used as a base class for the ClientChannel class and could be
 * useful for other classes that want to pass through most methods, but
 * override a few.
 * 
 * <P>Why not use inheritance? Because this lets you modify any kind of channel
 * in the same way. For instance, ClientChannel modifies a stub class created
 * by rmic. It would be difficult if not impossible to inherit from such a
 * class.
 * 
 * <P>The RemoteChannel class is actually just a thin shell that provides
 * remote access to a channel. Normally, each LocalChannel maintains a list
 * of RemoteChannels, each one representing a different amount of access
 * privileges (or a different identity if you prefer). Each of these
 * RemoteChannels uses a ControlledChannel to enforce its access privileges
 * when accessing the LocalChannel.
 * @see                         Channel
 */
class PassthroughChannel implements Channel
{

    /**
     * Constants that identify each public method for access control purposes.
     */
    static final int CHANNEL_METHOD_DESTROY = 0;
    static final int CHANNEL_METHOD_DUPLICATE = 1;
    static final int CHANNEL_METHOD_VALIDATE = 2;
    static final int CHANNEL_METHOD_REGISTER = 3;
    static final int CHANNEL_METHOD_UNREGISTER = 4;
    static final int CHANNEL_METHOD_CREATE_RM_STREAM_SOCKET = 5;
    static final int CHANNEL_METHOD_CREATE_RM_PACKET_SOCKET = 6;
    static final int CHANNEL_METHOD_ADD_CHANNEL_CHANGE_LISTENER = 7;
    static final int CHANNEL_METHOD_REMOVE_CHANNEL_CHANGE_LISTENER = 8;
    static final int CHANNEL_METHOD_ADD_REGISTERED_RECEIVER_COUNT_LISTENER = 
        9;
    static final int CHANNEL_METHOD_REMOVE_REGISTERED_RECEIVER_COUNT_LISTENER = 
        10;
    static final int CHANNEL_METHOD_ADD_RECEIVER_COMPLETION_COUNT_LISTENER = 
        11;
    static final int CHANNEL_METHOD_REMOVE_RECEIVER_COMPLETION_COUNT_LISTENER = 
        12;
    static final int CHANNEL_METHOD_ADD_PRINCIPAL_STATUS_CHANGE_LISTENER = 13;
    static final int CHANNEL_METHOD_REMOVE_PRINCIPAL_STATUS_CHANGE_LISTENER = 
        14;
    static final int CHANNEL_METHOD_SET_CHANNEL_NAME = 15;
    static final int CHANNEL_METHOD_SET_APPLICATION_NAME = 16;
    static final int CHANNEL_METHOD_SET_TRANSPORT_PROFILE = 17;
    static final int CHANNEL_METHOD_SET_DATA_START_TIME = 18;
    static final int CHANNEL_METHOD_SET_DATA_END_TIME = 19;
    static final int CHANNEL_METHOD_SET_SESSION_END_TIME = 20;
    static final int CHANNEL_METHOD_SET_LEAD_TIME = 21;
    static final int CHANNEL_METHOD_SET_LEAD_TIME_RANDOM_INTERVAL = 22;
    static final int CHANNEL_METHOD_SET_EXPIRATION_TIME = 23;
    static final int CHANNEL_METHOD_SET_REREGISTRATION_LEAD_TIME = 24;
    static final int 
	CHANNEL_METHOD_SET_REREGISTRATION_LEAD_TIME_RANDOM_INTERVAL = 25;
    static final int CHANNEL_METHOD_SET_MINIMUM_SPEED = 26;
    static final int CHANNEL_METHOD_SET_MAXIMUM_SPEED = 27;
    static final int CHANNEL_METHOD_SET_REGISTRATION_REQUIRED = 28;
    static final int CHANNEL_METHOD_SET_DATA_SIGNED = 29;
    static final int CHANNEL_METHOD_SET_ENCRYPTION_TYPE = 30;
    static final int CHANNEL_METHOD_SET_DECRYPTION_KEY = 31;
    static final int CHANNEL_METHOD_SET_ADDITIONAL_ADVERTISED_DATA = 32;
    static final int CHANNEL_METHOD_SET_ADDITIONAL_UNADVERTISED_DATA = 33;
    static final int CHANNEL_METHOD_SET_ABSTRACT = 34;
    static final int CHANNEL_METHOD_SET_CONTACT_NAME = 35;
    static final int CHANNEL_METHOD_SET_ENABLED = 36;
    static final int CHANNEL_METHOD_SET_ADVERTISING_REQUESTED = 37;
    static final int CHANNEL_METHOD_SET_ADVERTISEMENT_ADDRESS = 38;
    static final int CHANNEL_METHOD_SET_MULTIPLE_SENDERS_ALLOWED = 39;
    static final int CHANNEL_METHOD_ADD_RECEIVER = 40;
    static final int CHANNEL_METHOD_REMOVE_RECEIVER = 41;
    static final int CHANNEL_METHOD_GET_RECEIVER_COUNT = 42;
    static final int CHANNEL_METHOD_GET_RECEIVER_LIST = 43;
    static final int CHANNEL_METHOD_ADD_SENDER = 44;
    static final int CHANNEL_METHOD_REMOVE_SENDER = 45;
    static final int CHANNEL_METHOD_GET_SENDER_LIST = 46;
    static final int CHANNEL_METHOD_ADD_ADMINISTRATOR = 47;
    static final int CHANNEL_METHOD_REMOVE_ADMINISTRATOR = 48;
    static final int CHANNEL_METHOD_GET_ADMINISTRATOR_LIST = 49;
    static final int CHANNEL_METHOD_GET_CHANNEL_NAME = 50;
    static final int CHANNEL_METHOD_GET_APPLICATION_NAME = 51;
    static final int CHANNEL_METHOD_GET_TRANSPORT_PROFILE = 52;
    static final int CHANNEL_METHOD_GET_DATA_START_TIME = 53;
    static final int CHANNEL_METHOD_GET_DATA_END_TIME = 54;
    static final int CHANNEL_METHOD_GET_SESSION_END_TIME = 55;
    static final int CHANNEL_METHOD_GET_LEAD_TIME = 56;
    static final int CHANNEL_METHOD_GET_LEAD_TIME_RANDOM_INTERVAL = 57;
    static final int CHANNEL_METHOD_GET_EXPIRATION_TIME = 58;
    static final int CHANNEL_METHOD_GET_REREGISTRATION_LEAD_TIME = 59;
    static final int 
	CHANNEL_METHOD_GET_REREGISTRATION_LEAD_TIME_RANDOM_INTERVAL = 60;
    static final int CHANNEL_METHOD_GET_MINIMUM_SPEED = 61;
    static final int CHANNEL_METHOD_GET_MAXIMUM_SPEED = 62;
    static final int CHANNEL_METHOD_IS_REGISTRATION_REQUIRED = 63;
    static final int CHANNEL_METHOD_IS_DATA_SIGNED = 64;
    static final int CHANNEL_METHOD_GET_ENCRYPTION_TYPE = 65;
    static final int CHANNEL_METHOD_GET_DECRYPTION_KEY = 66;
    static final int CHANNEL_METHOD_GET_PUBLIC_KEY = 67;
    static final int CHANNEL_METHOD_GET_ADDITIONAL_ADVERTISED_DATA = 68;
    static final int CHANNEL_METHOD_GET_ADDITIONAL_UNADVERTISED_DATA = 69;
    static final int CHANNEL_METHOD_GET_ABSTRACT = 70;
    static final int CHANNEL_METHOD_GET_CONTACT_NAME = 71;
    static final int CHANNEL_METHOD_GET_ADVERTISING_REQUESTED = 72;
    static final int CHANNEL_METHOD_GET_ADVERTISEMENT_ADDRESS = 73;
    static final int CHANNEL_METHOD_IS_MULTIPLE_SENDERS_ALLOWED = 74;
    static final int CHANNEL_METHOD_IS_ENABLED = 75;
    static final int CHANNEL_METHOD_IS_VALID = 76;
    static final int CHANNEL_METHOD_IS_ADVERTISING = 77;
    static final int CHANNEL_METHOD_GET_CHANNEL_ID = 78;
    static final int CHANNEL_METHOD_GET_REGISTERED_RECEIVER_COUNT = 79;
    static final int CHANNEL_METHOD_GET_REGISTERED_RECEIVER_LIST = 80;
    static final int CHANNEL_METHOD_GET_CURRENT_SENDER_LIST = 81;
    static final int CHANNEL_METHOD_GET_REGISTRATION_FAILURE_COUNT = 82;
    static final int CHANNEL_METHOD_GET_TRANSPORT_RECEIVER_COUNT = 83;
    static final int CHANNEL_METHOD_LOG_STATUS = 84;
    static final int CHANNEL_METHOD_GET_STATUS = 85;
    static final int CHANNEL_METHOD_GET_ADVERTISEMENT_COUNT = 86;
    static final int CHANNEL_METHOD_GET_CURRENT_ADVERTISEMENT_INTERVAL = 87;
    static final int CHANNEL_METHOD_GET_ADVERTISEMENT_TIMESTAMP = 88;
    static final int CHANNEL_METHOD_GET_CREATION_TIME = 89;
    static final int CHANNEL_METHOD_SET_DYNAMIC_FILTER_LIST = 90;
    static final int CHANNEL_METHOD_GET_DYNAMIC_FILTER_LIST = 91;

    /**
     * Create a new PassthroughChannel that passes through calls to
     * a given channel.
     * 
     * @param channel the channel that the PassthroughChannel will access
     */
    PassthroughChannel(Channel channel) {
        c = channel;
    }

    /**
     * Check access control before allowing a method call. The default
     * implementation (here) allows all access to proceed. To deny access,
     * throw an UnauthorizedUserException.
     * 
     * @param methodID an int that identifies the method being called
     * (one of CHANNEL_METHOD_*)
     */
    void checkAccess(int methodID) throws UnauthorizedUserException {}

    /**
     * Destroys the Channel.  This causes all ChannelManagers to forget about 
     * the Channel.  It is never permissible to destroy a SAPChannel because 
     * it is not possible to communicate with the channel's owning PCM.
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void destroy() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_DESTROY);
        c.destroy();
    }

    /**
     * Duplicates the <code>Channel</code>. Creates a new channel exactly like 
     * this one, but with a new channel ID. It is never permissible to duplicate
     * a SAPChannel because it is not possible to communicate with the 
     * channel's owning PCM.
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Channel duplicate() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_DUPLICATE);

        return (new PassthroughChannel(c.duplicate()));
    }

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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void validate() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_VALIDATE);
        c.validate();
    }

    /**
     * Creates a ChannelRMStreamSocket for sending and/or receiving on
     * the channel.
     * 
     * @param sendReceive indicates whether this socket is to be used
     * for transmitting or receiving data. Valid input is
     * TransportProfile.SENDER, TransportProfile.RECEIVER, or
     * TransportProfile.SEND_RECEIVE.
     * 
     * @return a new ChannelRMStreamSocket
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public ChannelRMStreamSocket createRMStreamSocket(int sendReceive) 
            throws RMException, IOException, RemoteException, 
		   UnsupportedException {
        checkAccess(CHANNEL_METHOD_CREATE_RM_STREAM_SOCKET);

        return (c.createRMStreamSocket(sendReceive));
    }

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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public ChannelRMStreamSocket createRMStreamSocket(TransportProfile tp, 
            int sendReceive) throws RMException, IOException, 
                                    UnsupportedException, RemoteException {
        checkAccess(CHANNEL_METHOD_CREATE_RM_STREAM_SOCKET);

        return (c.createRMStreamSocket(tp, sendReceive));
    }

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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception IOException if an I/O error occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public ChannelRMPacketSocket createRMPacketSocket(int sendReceive) 
            throws UnsupportedException, RMException, IOException, 
		   RemoteException {
        checkAccess(CHANNEL_METHOD_CREATE_RM_PACKET_SOCKET);

        return (c.createRMPacketSocket(sendReceive));
    }

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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception IOException if an I/O error occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public ChannelRMPacketSocket createRMPacketSocket(TransportProfile tp, 
            int sendReceive) throws RMException, IOException, 
                                    UnsupportedException, RemoteException {
        checkAccess(CHANNEL_METHOD_CREATE_RM_PACKET_SOCKET);

        return (c.createRMPacketSocket(tp, sendReceive));
    }

    /**
     * Add a ChannelChangeListener to the listener list.
     * @param listener the listener to be added
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception IOException if an I/O error occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void addChannelChangeListener(ChannelChangeListener listener) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_ADD_CHANNEL_CHANGE_LISTENER);
        c.addChannelChangeListener(listener);
    }

    /**
     * Remove a ChannelChangeListener from the listener list.
     * @param listener the listener to be removed
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void removeChannelChangeListener(ChannelChangeListener listener) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_REMOVE_CHANNEL_CHANGE_LISTENER);
        c.removeChannelChangeListener(listener);
    }

    /**
     * Sets the channel name.
     * null means unknown or unspecified.
     * @param name the new channel name
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setChannelName(String name) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_CHANNEL_NAME);
        c.setChannelName(name);
    }

    /**
     * Sets the channel's application name.
     * null means unknown or unspecified.
     * @param name the new application name
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setApplicationName(String name) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_APPLICATION_NAME);
        c.setApplicationName(name);
    }

    /**
     * Sets the channel's transport profile. Because the Channel object may be
     * remote, the channel actually makes a copy of the TransportProfile 
     * provided and uses that.
     * null means unknown or unspecified.
     * @param profile the new transport profile
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setTransportProfile(TransportProfile profile) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_TRANSPORT_PROFILE);
        c.setTransportProfile(profile);
    }

    /**
     * Sets the channel's data start time. This date need not be set and if set 
     * is only advisory.
     * Data transmission may start at any time at the discretion of the 
     * channel's sender(s).
     * null means unknown or unspecified.
     * Because the channel may be remote, the channel actually makes a copy of 
     * the Date provided and uses that.
     * @param startTime the new data start time (null, the default, 
     * means unknown)
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setDataStartTime(Date startTime) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_DATA_START_TIME);
        c.setDataStartTime(startTime);
    }

    /**
     * Sets the channel's data end time. This date need not be set and if set is
     * only advisory.  Data transmission may end at any time at the discretion 
     * of the channel's sender(s).
     * null means unknown or unspecified.
     * Because the channel may be remote, the channel actually makes a copy 
     * of the Date provided and uses that.
     * @param endTime the new data end time (null, the default, means unknown)
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setDataEndTime(Date endTime) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_DATA_END_TIME);
        c.setDataEndTime(endTime);
    }

    /**
     * Sets the channel's session end time. This is a time by which 
     * the multicast transport session associated with a channel is expected 
     * to end. This date need not be set and if set is only advisory.
     * null means unknown or unspecified.
     * Because the channel may be remote, the channel actually makes a copy 
     * of the Date provided and uses that.
     * @param endTime the new session end time (null, the default, 
     * means unknown)
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setSessionEndTime(Date endTime) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_SESSION_END_TIME);
        c.setSessionEndTime(endTime);
    }

    /**
     * Sets the channel's suggested registration lead time. This is the time 
     * before the data start time at which receivers may begin to register.
     * This value need not be set and if set is only advisory.
     * 
     * @param leadTime lead time in seconds
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setLeadTime(int leadTime) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_LEAD_TIME);
        c.setLeadTime(leadTime);
    }

    /**
     * Sets the channel's suggested registration randomizer interval. This 
     * value specifies the period of time after the registration lead time 
     * over which registrations should be spread in order to spread out channel
     * registration activity.
     * This value need not be set and if set is only advisory.
     * 
     * @param interval randomizer interval in seconds
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setLeadTimeRandomInterval(int interval) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_LEAD_TIME_RANDOM_INTERVAL);
        c.setLeadTimeRandomInterval(interval);
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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setExpirationTime(Date expirationTime) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_EXPIRATION_TIME);
        c.setExpirationTime(expirationTime);
    }

    /**
     * Sets the minimum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory. 
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually drop below this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @param speed the new minimum speed
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setMinimumSpeed(int speed) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_MINIMUM_SPEED);
        c.setMinimumSpeed(speed);
    }

    /**
     * Sets the maximum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory. 
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually rise above this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @param speed the new minimum speed
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setMaximumSpeed(int speed) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_MAXIMUM_SPEED);
        c.setMaximumSpeed(speed);
    }

    /**
     * Sets an optional field for additional channel-specific data to be 
     * included in advertisements. As much of this field as possible is 
     * included in SAP advertisements, up to about 600 bytes.
     * null means unknown or unspecified.
     * @param data the new additional advertised data (null means unknown)
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setAdditionalAdvertisedData(String data) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_ADDITIONAL_ADVERTISED_DATA);
        c.setAdditionalAdvertisedData(data);
    }

    /**
     * Sets an optional field for additional channel-specific data to be 
     * included in the channel, but not included in advertisements.
     * null means unknown or unspecified.
     * @param data the new additional unadvertised data (null means unknown)
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setAdditionalUnadvertisedData(String data) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_ADDITIONAL_UNADVERTISED_DATA);
        c.setAdditionalUnadvertisedData(data);
    }

    /**
     * Sets an optional field containing an abstract describing the channel. 
     * As much of this field as possible is included in SAP advertisements, 
     * up to about 600 bytes.
     * null means unknown or unspecified.
     * @param data the new abstract
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setAbstract(String data) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_ABSTRACT);
        c.setAbstract(data);
    }

    /**
     * Sets an optional field containing a contact name for the channel.
     * null means unknown or unspecified.
     * @param data the new contact name
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setContactName(String data) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_CONTACT_NAME);
        c.setContactName(data);
    }

    /**
     * Sets whether the channel is enabled. Disabled channels are not 
     * accessible to anyone without administrator access. Enabled channels must 
     * be valid (as described under the validate method).
     * @param b if <code>true</code>, enable the channel;
     * otherwise, disable it
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setEnabled(boolean b) throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_ENABLED);
        c.setEnabled(b);
    }

    /**
     * Sets whether channel advertising is requested.
     * 
     * <P><strong>NOTE:</strong> A channel is only advertised if it is enabled 
     * and channel advertising has been requested.
     * @param advertising <code>true</code> if the channel should be advertised;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setAdvertisingRequested(boolean newAdvertising) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_ADVERTISING_REQUESTED);
        c.setAdvertisingRequested(newAdvertising);
    }

    /**
     * Sets the requested multicast address to be used for SAP advertisements.
     * The value null means use the default SAP advertisement address.
     * Note that the TTL used for advertisements is the same as that specified 
     * in the TransportProfile.
     * @param address the new multicast address
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception IOException if the address is not a multicast address
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setAdvertisementAddress(InetAddress address) 
            throws RMException, IOException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_ADVERTISEMENT_ADDRESS);
        c.setAdvertisementAddress(address);
    }

    /**
     * Sets if multiple senders are allowed on this channel.
     * @param multiple <code>true</code> if multiple senders should be allowed 
     * on this channel;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public void setMultipleSendersAllowed(boolean multiple) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_MULTIPLE_SENDERS_ALLOWED);
        c.setMultipleSendersAllowed(multiple);
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
    public void setDynamicFilterList(Vector list) 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_SET_DYNAMIC_FILTER_LIST);
        c.setDynamicFilterList(list);
    }

    /**
     * Returns the channel name.
     * null means unknown or unspecified.
     * @return the channel name
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public String getChannelName() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_CHANNEL_NAME);

        return (c.getChannelName());
    }

    /**
     * Returns the channel's application name.
     * null means unknown or unspecified.
     * @return the channel's application name
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public String getApplicationName() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_APPLICATION_NAME);

        return (c.getApplicationName());
    }

    /**
     * Returns a copy of the channel's TransportProfile. Because the profile
     * returned is a copy of the channel's TransportProfile, it will not be
     * updated if the channel's TransportProfile changes. Likewise, changing
     * it will not affect the channel (unless Channel.setTransportProfile() 
     * is called).  null means unknown or unspecified.
     * @return a copy of the channel's TransportProfile
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public TransportProfile getTransportProfile() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_TRANSPORT_PROFILE);

        return (c.getTransportProfile());
    }

    /**
     * Gets a copy of the channel's data start time. This date need not be 
     * set and if set is only advisory. Data transmission may start at any 
     * time at the discretion of the channel's sender(s).
     * Because the Date object returned is a copy of the channel's data start 
     * time, it will not be updated if the channel's data start time changes. 
     * Likewise, changing it will not affect the channel 
     * (unless Channel.setDataStartTime() is called).
     * null means unknown or unspecified.
     * @return the data start time
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Date getDataStartTime() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_DATA_START_TIME);

        return (c.getDataStartTime());
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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Date getDataEndTime() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_DATA_END_TIME);

        return (c.getDataEndTime());
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
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Date getSessionEndTime() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_SESSION_END_TIME);

        return (c.getSessionEndTime());
    }

    /**
     * Gets the channel's suggested registration lead time. This is the time 
     * before the data start time at which receivers may begin to register.
     * This value need not be set and if set is only advisory.
     * 
     * @return lead time in seconds
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public int getLeadTime() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_LEAD_TIME);

        return (c.getLeadTime());
    }

    /**
     * Gets the channel's suggested registration randomizer interval. 
     * This value specifies the period of time after the registration lead time 
     * over which registrations should be spread in order to spread out channel 
     * registration activity.  This value need not be set and if set is 
     * only advisory.
     * 
     * @return randomizer interval in seconds
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public int getLeadTimeRandomInterval() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_LEAD_TIME_RANDOM_INTERVAL);

        return (c.getLeadTimeRandomInterval());
    }

    /**
     * Gets a copy of the channel's expiration time. This is a time at which 
     * these channel characteristics expire. The receiver should have 
     * reregistered before this time and begun using the new characteristics 
     * received during reregistration.  This date need not be set and if set 
     * is only advisory.  Because the Date object returned is a copy of the 
     * channel's data end time, it will not be updated if the channel's data 
     * end time changes. Likewise, changing it will not affect the channel 
     * (unless Channel.setExpirationTime() is called).
     * null means unknown or unspecified.
     * 
     * @return a copy of the channel expiration time
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Date getExpirationTime() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_EXPIRATION_TIME);

        return (c.getExpirationTime());
    }

    /**
     * Gets the minimum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually drop below this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @return the minimum speed
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public int getMinimumSpeed() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_MINIMUM_SPEED);

        return (c.getMinimumSpeed());
    }

    /**
     * Gets the maximum speed at which channel sends data in bits per second.
     * This speed need not be set and if set is only advisory.
     * 0 means unknown or unspecified.
     * <P>The actual speed of delivery will vary over the transmission and may
     * actually rise above this value at any given time for a given receiver,
     * especially if the sender is sending more than one channel over the same
     * multicast address.
     * @return the minimum speed
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public int getMaximumSpeed() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_MAXIMUM_SPEED);

        return (c.getMaximumSpeed());
    }

    /**
     * Gets an optional field for additional channel-specific data to be 
     * included in advertisements.
     * null means unknown or unspecified.
     * @return the additional advertised data
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public String getAdditionalAdvertisedData() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ADDITIONAL_ADVERTISED_DATA);

        return (c.getAdditionalAdvertisedData());
    }

    /**
     * Gets an optional field for additional channel-specific data to be 
     * included in the channel, but not included in advertisements.
     * null means unknown or unspecified.
     * @return the additional unadvertised data
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public String getAdditionalUnadvertisedData() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ADDITIONAL_UNADVERTISED_DATA);

        return (c.getAdditionalUnadvertisedData());
    }

    /**
     * Gets an optional field containing an abstract describing the channel.
     * null means unknown or unspecified.
     * @return the abstract
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public String getAbstract() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ABSTRACT);

        return (c.getAbstract());
    }

    /**
     * Gets an optional field containing a contact name for the channel.
     * null means unknown or unspecified.
     * @return the contact name
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public String getContactName() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_CONTACT_NAME);

        return (c.getContactName());
    }

    /**
     * Tests if channel advertising has been requested.
     * @return <code>true</code> if channel advertising has been requested;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public boolean getAdvertisingRequested() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ADVERTISING_REQUESTED);

        return (c.getAdvertisingRequested());
    }

    /**
     * Returns the multicast address used for SAP advertisements.
     * The value null means use the default SAP advertisement address.
     * @return the multicast address used for SAP advertisements
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public InetAddress getAdvertisementAddress() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ADVERTISEMENT_ADDRESS);

        return (c.getAdvertisementAddress());
    }

    /**
     * Tests if multiple senders are allowed on this channel.
     * @return <code>true</code> if multiple senders are allowed on this 
     * channel;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public boolean isMultipleSendersAllowed() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_IS_MULTIPLE_SENDERS_ALLOWED);

        return (c.isMultipleSendersAllowed());
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
     * if the current principal does not have authorization to perform this 
     * action
     */
    public Vector getDynamicFilterList() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_DYNAMIC_FILTER_LIST);

        return (c.getDynamicFilterList());
    }

    /**
     * Tests whether the channel is enabled. Disabled channels are not 
     * accessible to anyone without administrator access. Enabled channels must 
     * be valid (as described under the validate method).
     * @return <code>true</code> if the channel is enabled;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public boolean isEnabled() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_IS_ENABLED);

        return (c.isEnabled());
    }

    /**
     * Tests whether the channel is valid. A channel is valid if, as far 
     * as can be determined, the channel could be used to send and receive 
     * data (subject to access control restrictions).
     * Possible causes for invalid channels include not providing a transport 
     * profile or requesting support for multiple senders and providing a 
     * transport profile that cannot support this.
     * @return <code>true</code> if the channel is valid;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public boolean isValid() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_IS_VALID);

        return (c.isValid());
    }

    /**
     * Tests if the channel is being advertised.
     * 
     * <P><strong>NOTE:</strong> A channel is only advertised if it is enabled 
     * and channel advertising has been requested.
     * @return <code>true</code> if the channel is being advertised;
     * <code>false</code> otherwise
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public boolean isAdvertising() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_IS_ADVERTISING);

        return (c.isAdvertising());
    }

    /**
     * Gets the channel ID (a long that identifies the channel uniquely,
     * at least within the ChannelManager).
     * @return the channel ID
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public long getChannelID() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_CHANNEL_ID);

        return (c.getChannelID());
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
        checkAccess(CHANNEL_METHOD_GET_TRANSPORT_RECEIVER_COUNT);

        return (c.getTransportReceiverCount());
    }

    /**
     * Gets the count of times the channel has been advertised using SAP.
     * @return the count of times the channel has been advertised
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public long getAdvertisementCount() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ADVERTISEMENT_COUNT);

        return (c.getAdvertisementCount());
    }

    /**
     * Gets the number of seconds between the last two SAP advertisements.
     * @return the number of seconds between the last two SAP advertisements
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public int getCurrentAdvertisementInterval() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_CURRENT_ADVERTISEMENT_INTERVAL);

        return (c.getCurrentAdvertisementInterval());
    }

    /**
     * Gets the time the channel was last advertised via SAP.
     * @return the time the channel was last advertised via SAP
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Date getAdvertisementTimestamp() 
            throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_ADVERTISEMENT_TIMESTAMP);

        return (c.getAdvertisementTimestamp());
    }

    /**
     * Gets the time the channel was created.
     * @return the time the channel was created
     * @exception RMException if a reliable-multicast-related exception occurs
     * @exception RemoteException if an RMI-related exception occurs
     */
    public Date getCreationTime() throws RMException, RemoteException {
        checkAccess(CHANNEL_METHOD_GET_CREATION_TIME);

        return (c.getCreationTime());
    }

    /**
     * Test method to check if the cipher functionality is being used.
     * @return <code>true</code> if the channel is being advertised;
     * <code>false</code> otherwise
     * 
     */
    public boolean isUsingCipher() {
        return c.isUsingCipher();
    }

    /**
     * Enables the use of cipher functionality.
     * 
     */
    public void enableCipher() {
        c.enableCipher();
    }

    /**
     * Disables the use of cipher functionality.
     * 
     */
    public void disableCipher() {
        c.disableCipher();
    }

    /**
     * gets the name of the specification file that is to be used to 
     * initialize the Cipher.
     * 
     * @return Name of the filename used to initialize Cipher module.
     */
    public String getCipherSpecFileName() {
        return c.getCipherSpecFileName();
    }

    /**
     * sets the name of the specification file that is to be used to 
     * initialize the Cipher.
     * 
     * @param cipherSpecFileName - the Name of the filename used to 
     * initialize Cipher module.
     */
    public void setCipherSpecFileName(String cipherSpecFileName) {
        c.setCipherSpecFileName(cipherSpecFileName);
    }

    private Channel c;

}
