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
 * UMTransportProfile.java
 * 
 * Module Description:
 * 
 * The class implements the TransportProfile object for the
 * UnreliableTransport..
 */
package com.sun.multicast.reliable.transport.um;

import java.net.InetAddress;
import java.io.IOException;
import java.net.SocketException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.RMStreamSocket;
import com.sun.multicast.reliable.transport.TransportProfile;

/**
 * A TransportProfile for the Unreliable Multicast transport.
 * 
 * <P>The only transport-specific public methods that are added
 * by this class are getDataRate and setDataRate, which allow a
 * maximum data rate to be set to throttle outgoing data. This
 * avoids overrunning network elements.
 * 
 * @see com.sun.multicast.reliable.transport.TransportProfile
 */
public class UMTransportProfile implements TransportProfile, Cloneable, 
                                           java.io.Serializable {
    private String name = "Unreliable Multicast Transport";
    private InetAddress address = null;
    private int port = 0;
    private byte ttl = 1;
    private boolean multiSender = false;
    private boolean ordered = false;
    private long dataRate = 65535;
    private boolean useAuthentication = false;
    private String authenticationSpecFileName = null;
    private String authenticationSpecPassword = null;

    /**
     * Constructs an empty UMTransportProfile.
     */
    private UMTransportProfile() {}

    /**
     * Creates a new Unreliable Multicast TransportProfile.
     * The multicast address and port must be specified
     * for this constructor.
     * 
     * @param address a multicast address for this TransportProfile.
     * @param port the port number for this TransportProfile.
     * 
     * @exception InvalidMulticastAddressException if the address specified
     * is not a multicast address.
     */
    public UMTransportProfile(InetAddress address, int port) 
            throws InvalidMulticastAddressException {
        if (address.isMulticastAddress()) {
            this.address = address;
            this.port = port;
        } else {
            throw new InvalidMulticastAddressException(
		"The address specified is not a valid multicast address");
        }
    }

    /**
     * Creates an RMStreamSocket using this TransportProfile as input.
     * Unreliable multicast cannot support a stream interface, since
     * it does not include support for repairing lost data. Therefore,
     * this method always throws UnsupportedException in this class.
     * 
     * @param sendReceive UMTransportProfile doesn't care about this
     * parameter.
     * @return a new RMStreamSocket (but nothing is ever returned here).
     * @exception UnsupportedException always, since this transport
     * does not support a stream interface.
     */
    public RMStreamSocket createRMStreamSocket(int sendReceive) 
	throws UnsupportedException {

        throw new UnsupportedException(
	    "createRMStreamSocket isn't supported for Unreliable Multicast.");
    }

    /**
     * Creates an RMPacketSocket using this TransportProfile as input.
     * 
     * @param sendReceive UMTransportProfile doesn't care about this
     * parameter.
     * @return the new RMPacketSocket
     * @exception UnsupportedException if the transport does not
     * support a packet interface.
     * @exception InvalidTransportProfileException if the TransportProfile
     * is not valid
     * @exception java.io.IOException if an I/O exception occurs.
     */
    public RMPacketSocket createRMPacketSocket(int sendReceive) 
            throws UnsupportedException, InvalidTransportProfileException, 
                   IOException {
        validate();

        UMPacketSocket so = new UMPacketSocket();

        so.connect(this);

        return (RMPacketSocket) so;
    }

    /**
     * Returns the name of this transport. The transport sets the name field.
     * Applications cannot modify this field.
     * 
     * @return the name of the transport
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the multicast address for this TransportProfile.
     * 
     * @return the multicast address for this TransportProfile
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Returns the multicast port number for this TransportProfile.
     * 
     * @return the multicast port number for this transport.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the time-to-live for this TransportProfile. The time-to-live
     * value indicates the range of multicast messages.
     * 
     * @return the time-to-live value for this TransportProfile.
     */
    public byte getTTL() {
        return ttl;
    }

    /**
     * Returns the maximum send rate for this TransportProfile (in
     * bytes per second).
     * 
     * @return the current data rate.
     */
    public long getDataRate() {
        return dataRate;
    }

    /**
     * gets the name of the authenticationSpec filename to use for
     * initialization.
     */
    public String getAuthenticationSpecFileName() {
        return authenticationSpecFileName;
    }

    /**
     * Determines if multiple senders are supported with this TransportProfile.
     * 
     * @return true if multiple senders are supported with this
     * TransportProfile; false otherwise.
     */
    public boolean isMultiSender() {
        return multiSender;
    }

    /**
     * Returns the value of the ordered flag. If the application requires
     * that the data is returned to the application in the order which it
     * was sent, the ordered flag needs to be set. If the application doesn't
     * care which order the data arrives in, this flag can be set to false.
     * This flag is only valid for RMPacketSockets and most transports can
     * only support ordering for a single sender. Check with the particular
     * transport if global ordering is required.
     * 
     * @return the value of the ordered flag.
     */
    public boolean isOrdered() {
        return ordered;
    }

    /**
     * Returns the value of the authentication flag. If the flag is
     * set(true), it indicates that the data packets are authenticated.
     * 
     * @return true indicates that the data packets are authenticated
     * false indicates that the data packets are not
     * authenticated/signed.
     */
    public boolean isUsingAuthentication() {
        return useAuthentication;
    }

    /**
     * Sets the multicast address for this TransportProfile.
     * 
     * @param address the multicast address for this TransportProfile.
     * @exception InvalidMulticastAddressException if an
     * the address specified is not a multicast address.
     */
    public void setAddress(InetAddress address) 
            throws InvalidMulticastAddressException {
        if (address.isMulticastAddress()) {
            this.address = address;
        } else {
            throw new InvalidMulticastAddressException(
		"The address specified is not a multicast address!");
        }
    }

    /**
     * Sets the multicast port number for this TransportProfile.
     * 
     * @param port the new multicast port number.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the value of the ordered flag. Setting this flag to true
     * indicates that all data is to be forwarded to the application
     * in the order it was sent. Setting this flag to false indicates
     * that the application will get the data in the order that it
     * was received, which may not be the order that it was sent.
     * 
     * @param ordered the value of the ordered flag.
     */
    public void setOrdered(boolean ordered) {
        this.ordered = ordered;
    }

    /**
     * Sets the value for the Time-to-live. The ttl indicates the range of
     * the multicast messages sent on the multicast address/port. The
     * default value is 1 (local area).
     * 
     * @param ttl the value of the time-to-live parameter.
     */
    public void setTTL(byte ttl) {
        this.ttl = ttl;
    }

    /**
     * Sets the value of the multisender flag. If the application
     * wishes to support multiple senders with this TransportProfile,
     * this flag must be set to true.
     * 
     * @param multisender true if the application wishes to support
     * multiple senders; false otherwise
     * 
     * @exception UnsupportedException if the transport does not
     * support multiple senders.
     */
    public void setMultiSender(boolean multiSender) 
            throws UnsupportedException {
        this.multiSender = multiSender;
    }

    /**
     * Sets the maximum send rate for this TransportProfile (in bytes per
     * second).
     * 
     * @param dataRate the new data rate.
     */
    public void setDataRate(long dataRate) {
        this.dataRate = dataRate;
    }

    /**
     * Throws an exception if the TransportProfile is not valid. 
     * A TransportProfile is valid if, as far as can be determined, 
     * it could be used to send or receive data. Possible causes for 
     * invalid TransportProfiles include not setting the multicast address, 
     * port, and TTL (or other transport-specific problems).
     * @exception 
     *     com.sun.multicast.reliable.channel.InvalidTransportProfileException
     * if the TransportProfile is not valid
     */
    private void validate() throws InvalidTransportProfileException {
        if (address.isMulticastAddress() == false) {
            throw new InvalidTransportProfileException(
		"invalid multicast address");
        } 

        // Assuming port # is 16 bits wide. Fix me if different

        if ((port < 0) || (port > 65535)) {
            throw new InvalidTransportProfileException(
		"invalid multicast port");
        } 
        if (dataRate <= 0) {
            throw new InvalidTransportProfileException("invalid data rate");
        } 
        if (ttl == 0) {
            throw new InvalidTransportProfileException("invalid TTL");
        } 
    }

    /**
     * Tests whether this TransportProfile is valid. A TransportProfile
     * is valid if, as far as can be determined, it could be used to send
     * or receive data. Possible causes for invalid TransportProfiles
     * include not setting the multicast address, port, and TTL (or other
     * transport-specific problems).
     * @return <code>true</code> if the TransportProfile is valid;
     * <code>false</code> otherwise
     */
    public boolean isValid() {
        boolean valid = true;

        try {
            validate();
        } catch (InvalidTransportProfileException e) {
            valid = false;
        }

        return (valid);
    }

    /**
     * Create a new UMTransportProfile object and return it to the caller.
     * 
     * @return a copy of this UMTransportProfile object.
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException ce) {
            throw new InternalError("CloneNotSupportedError should not happen");
        }
    }

    /**
     * enable the use of authentication.
     */
    public void enableAuthentication() {
        useAuthentication = true;
    }

    /**
     * disables the use of Authentication
     */
    public void disableAuthentication() {
        useAuthentication = false;
    }

    /**
     * sets the name of the authenticationSpec filename to use for
     * initialization.
     */
    public void setAuthenticationSpecFileName(String specFileName) {
        authenticationSpecFileName = specFileName;
    }

    /**
     * Gets the password for the authenticationSpec
     * @return authenticationSpecPassword currently specified.
     */
    public String getAuthenticationSpecPassword() {
        return authenticationSpecPassword;
    }

    /**
     * Sets the password for the authenticationSpec
     * @param password the password for the authenticationSpec.
     */
    public void setAuthenticationSpecPassword(String password) {
        authenticationSpecPassword = password;
    }

}

