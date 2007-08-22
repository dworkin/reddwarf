package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;

public abstract class MembershipKey {

    /**
     * Initializes a new instance of this class.
     */
    protected MembershipKey() {
        // empty
    }

    /**
     * Tells whether or not this membership is valid.
     */
    public abstract boolean isValid();

    /**
     * Drop membership.
     */
    public abstract void drop() throws IOException;

    /**
     * Block multicast packets from the given source address.
     */
    public abstract MembershipKey block(InetAddress source)
        throws IOException;

    /**
     * Unblock multicast packets from the given source address that was
     * previously blocked using the block method.
     */
    public abstract MembershipKey unblock(InetAddress source)
        throws IOException;

    /**
     * Returns the channel for which this membership key was created.
     */
    public abstract MulticastChannel getChannel();

    /**
     * Returns the multicast group for which this membership key was
     * created.
     */
    public abstract InetAddress getGroup();

    /**
     * Returns the network interface for which this membership key was
     * created.
     */
    public abstract NetworkInterface getNetworkInterface();

    /**
     * Returns the source address if this membership key is source specific,
     * or null if this membership is not source specific.
     */
    public abstract InetAddress getSourceAddress();
}
