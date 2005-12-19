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
 * TransportProfile.java
 */
package com.sun.multicast.reliable.transport;

import java.net.InetAddress;
import java.io.IOException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;

/**
 * A TransportProfile is an object that contains the parameters
 * required to initialize and establish a multicast transport session. Each
 * transport must define at least one class that implements the
 * TransportProfile interface. This class may have additional
 * transport specific methods, but must support the minimal methods
 * defined in this interface.
 * 
 * <P>One common way to use a TransportProfile is for a sender to
 * create a transport-specific object that implements TransportProfile
 * and send it to a set of receivers via object serialization. The
 * receivers may then use the methods of the TransportProfile interface
 * to join the multicast transport session and receive data. Of course,
 * there are many other ways to use a TransportProfile.
 */
public interface TransportProfile extends java.lang.Cloneable, 
                                          java.io.Serializable {

    /**
     * A constant passed to createRM*Socket to indicate that the
     * socket will only be used for sending data.
     */
    static final int SENDER = 1;

    /**
     * A constant passed to createRM*Socket to indicate that the
     * socket will only be used for receiving data.
     */
    static final int RECEIVER = 2;

    /**
     * A constant passed to createRM*Socket to indicate that the
     * socket will be used for sending and receiving data.
     */
    static final int SEND_RECEIVE = 3;

    /**
     * A constant passed to createRM*Socket to indicate that the
     * socket will be used to start a multicast data repair node.
     * For such a socket, the receive() function does not return
     * data, but only exceptions when the session ends or aborts.
     */
    static final int REPAIR_NODE = 4;

    /**
     * Creates an RMStreamSocket using this TransportProfile as input.
     * 
     * @param sendReceive indicates whether the socket created is for
     * sending data, receiving data, or both. Valid values are SENDER,
     * RECEIVER, and SEND_RECEIVE. Some transports may not support
     * SEND_RECEIVE.
     * @return a new RMStreamSocket
     * @exception UnsupportedException if the transport does not
     * support a stream interface or does not support the specified
     * sendReceive value.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    RMStreamSocket createRMStreamSocket(int sendReceive) 
            throws UnsupportedException, InvalidTransportProfileException, 
                   IOException, RMException;

    /**
     * Creates an RMPacketSocket using this TransportProfile as input.
     * 
     * @param sendReceive indicates whether the socket created is for
     * sending data, receiving data, or both. Valid values are SENDER,
     * RECEIVER, and SEND_RECEIVE. Some transports may not support
     * SEND_RECEIVE.
     * @return a new RMPacketSocket
     * @exception UnsupportedException if the transport does not
     * support a packet interface or does not support the specified
     * sendReceive value.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    RMPacketSocket createRMPacketSocket(int sendReceive) 
            throws UnsupportedException, InvalidTransportProfileException, 
                   IOException, RMException;

    /**
     * Returns the name of this transport. The transport sets the name field.
     * Applications cannot modify this field.
     * 
     * @return the name of the transport
     */
    String getName();

    /**
     * Returns the multicast address for this TransportProfile.
     * 
     * @return the multicast address for this TransportProfile (may be null)
     */
    InetAddress getAddress();

    /**
     * Returns the multicast port number for this TransportProfile.
     * 
     * @return the multicast port number for this transport.
     */
    int getPort();

    /**
     * Returns the time-to-live for this TransportProfile. The time-to-live
     * value indicates the range of multicast messages.
     * 
     * @return the time-to-live value for this TransportProfile.
     */
    byte getTTL();

    /**
     * Returns the name of the authenticationSpec filename to use for
     * initialization.
     * 
     * @return the name of the authenticationSpec filename to use 
     * for initialization.
     */
    String getAuthenticationSpecFileName();

    /**
     * Returns the password for the authenticationSpec.
     * 
     * @return the password for the authenticationSpec.
     */
    String getAuthenticationSpecPassword();

    /**
     * Determines if multiple senders are supported with this TransportProfile.
     * 
     * @return true if multiple senders are supported with this
     * TransportProfile; false otherwise.
     */
    boolean isMultiSender();

    /**
     * Method to test if Packet Ordering option is enabled.If the
     * application requires that the data is returned to the application
     * in the order which it was sent, the ordered flag needs to be set.
     * If the application doesn't care which order the data arrives in,
     * this flag can be set to false.
     * This flag is only valid for RMPacketSockets and most transports can
     * only support ordering for a single sender. Check with the particular
     * transport if global ordering is required.
     * 
     * @return the value of the ordered flag.
     */
    boolean isOrdered();

    /**
     * Returns the value of the authentication flag. If the flag is
     * set(true), it indicates that the data packets are authenticated.
     * 
     * @return true indicates that the data packets are authenticated
     * false indicates that the data packets are not
     * authenticated/signed.
     */
    boolean isUsingAuthentication();

    /**
     * Sets the multicast address for this TransportProfile.
     * 
     * @param address the new multicast address.
     * @exception InvalidMulticastAddressException if an
     * the address specified is not a multicast address.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setAddress(InetAddress address) 
            throws InvalidMulticastAddressException, RMException;

    /**
     * Sets the multicast port number for this TransportProfile.
     * 
     * @param port the new multicast port number.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setPort(int port) throws RMException;

    /**
     * Sets the Packet Ordering preference in the transport profile.
     * Setting this flag to true indicates that all data is to be
     * forwarded to the application in the order it was sent. Setting
     * this flag to false indicates that the application will get the
     * data in the order that it was received, which may not be the order
     * that it was sent.
     * 
     * @param ordered the new value of the ordered flag.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setOrdered(boolean ordered) throws RMException;

    /**
     * Sets the value for the Time-to-live. The ttl indicates the range of
     * the multicast messages sent on the multicast address/port. The
     * default value is 1 (local area).
     * 
     * @param ttl the value of the time-to-live parameter.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setTTL(byte ttl) throws RMException;

    /**
     * Sets the value of the multisender flag. If the application
     * wishes to support multiple senders with this TransportProfile,
     * this flag must be set to true.
     * 
     * @param multisender true if the application wishes to support
     * multiple senders; false otherwise
     * @exception UnsupportedException if the transport does not
     * support multiple senders.
     * @exception RMException if a reliable-multicast-related exception occurs
     */
    void setMultiSender(boolean multisender) 
            throws UnsupportedException, RMException;

    /**
     * Tests whether this TransportProfile is valid. A TransportProfile
     * is valid if, as far as can be determined, it could be used to send
     * or receive data. Possible causes for invalid TransportProfiles
     * include not setting the multicast address, port, and TTL (or other
     * transport-specific problems).
     * @return <code>true</code> if the TransportProfile is valid;
     * <code>false</code> otherwise
     */
    boolean isValid();

    /**
     * Creates a new transport profile with all the attributes of the
     * current TransportProfile.
     * 
     * @return a new TransportProfile object.
     */
    Object clone();

    /**
     * Enables the use of authentication.
     */
    void enableAuthentication();

    /**
     * Disables the use of Authentication.
     */
    void disableAuthentication();

    /**
     * Sets the name of the authentication Spec filename to use for
     * initialization.
     * @param specFileName Authentication Spec file name.
     */
    void setAuthenticationSpecFileName(String specFileName);

    /**
     * Sets the password for the authentication Spec.
     * @param password Authentication Spec password.
     */
    void setAuthenticationSpecPassword(String password);
}

