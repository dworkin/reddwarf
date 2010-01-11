/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * A token representing the membership of an Internet Protocol (IP)
 * multicast group.
 * <p>
 * A membership key may represent a membership to receive all datagrams sent
 * to the group, or it may be <em>source-specific</em>, meaning that it
 * represents a membership that receives only datagrams from a specific
 * source address. Whether or not a membership key is source-specific may be
 * determined by invoking its {@link #getSourceAddress} method.
 * <p>
 * A membership key is valid upon creation and remains valid until the
 * membership is dropped by invoking the {@link #drop} method, or the
 * channel is closed. The validity of the membership key may be tested by
 * invoking its {@link #isValid} method.
 * <p>
 * Where a membership key is not source specific and the underlying
 * operation system supports source filtering, then the {@link #block} and
 * {@link #unblock} methods can be used to block or unblock multicast
 * packets from particular source addresses.
 * 
 * @see MulticastChannel
 */
public abstract class MembershipKey {

    /**
     * Initializes a new instance of this class.
     */
    protected MembershipKey() {
        // empty
    }

    /**
     * Tells whether or not this membership is valid.
     * <p>
     * A multicast group membership is valid upon creation and remains valid
     * until the membership is dropped by invoking the {@link #drop} method,
     * or the channel is closed.
     * 
     * @return {@code true} if this membership key is valid, {@code false}
     *         otherwise
     */
    public abstract boolean isValid();

    /**
     * Drop membership.
     * <p>
     * If the membership key represents a membership to receive all
     * datagrams then the membership is dropped and the channel will no
     * longer receive any datagrams sent to the group. If the membership key
     * is source specific then the channel will no longer receive datagrams
     * sent to the group from that source address.
     * <p>
     * After membership is dropped it may still be possible to receive
     * datagams sent to the group. This can arise when datagrams are waiting
     * to be received in the socket's receive buffer.
     * <p>
     * Upon return, this membership object will be {@link #isValid invalid}.
     * If the multicast group membership is already invalid then invoking
     * this method has no effect. Once a multicast group membership is
     * invalid, it remains invalid forever.
     * 
     * @throws IOException if an I/O error occurs
     */
    public abstract void drop() throws IOException;

    /**
     * Block multicast packets from the given source address.
     * <p>
     * If this membership key is not source-specific, and the underlying
     * operating system supports source filtering, then this method blocks
     * multicast packets from the given source address. If the given source
     * address is already blocked then this method has no effect. After a
     * source address is blocked it may still be possible to receive
     * datagams from that source. This can arise when datagrams are waiting
     * to be received in the socket's receive buffer.
     * 
     * @param source the source address to block
     * @return this membership key
     * @throws IllegalArgumentException if the {@code source} parameter is
     *         not a unicast address or is not the same address type as the
     *         multicast group
     * @throws IllegalStateException i this membership key is source
     *         specific or is no longer valid
     * @throws UnsupportedOperationException if the underlying operating
     *         system does not support source filtering
     * @throws IOException if an I/O error occurs
     */
    public abstract MembershipKey block(InetAddress source)
        throws IOException;

    /**
     * Unblock multicast packets from the given source address that was
     * previously blocked using the {@link #block} method.
     * 
     * @param source a list of source addresses to unblock
     * @return this membership key
     * @throws IllegalStateException if the given source address is not
     *         currently blocked or the membership key is no longer valid
     * @throws IOException if an I/O error occurs
     */
    public abstract MembershipKey unblock(InetAddress source)
        throws IOException;

    /**
     * Returns the channel for which this membership key was created. This
     * method will continue to return the channel even after the membership
     * is dropped.
     * 
     * @return the channel
     */
    public abstract MulticastChannel getChannel();

    /**
     * Returns the multicast group for which this membership key was
     * created. This method will continue to return the group even after the
     * membership is dropped.
     * 
     * @return the multicast group
     */
    public abstract InetAddress getGroup();

    /**
     * Returns the network interface for which this membership key was
     * created. This method will continue to return the network interface
     * even after the membership is dropped or the channel is closed.
     * 
     * @return the network interface
     */
    public abstract NetworkInterface getNetworkInterface();

    /**
     * Returns the source address if this membership key is source specific,
     * or {@code null} if this membership is not source specific.
     * 
     * @return the source address if this membership key is source specific,
     *         otherwise {@code null}
     */
    public abstract InetAddress getSourceAddress();
}
