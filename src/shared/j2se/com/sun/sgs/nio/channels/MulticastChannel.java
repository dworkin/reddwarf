package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;

public interface MulticastChannel extends NetworkChannel {

    /**
     * Joins a multicast group to begin receiving all datagrams sent to the
     * group.
     */
    MembershipKey join(InetAddress group, NetworkInterface interf)
        throws IOException;

    /**
     * Joins a multicast group to begin receiving datagrams sent to the
     * group from a given source address.
     */
    MembershipKey join(InetAddress group, NetworkInterface interf,
        InetAddress source) throws IOException;
}
