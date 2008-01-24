package com.sun.sgs.example.hack.client.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public interface ClientChannelListener {

    // N.B. needs to pass {@code null} for the
    // server's sender id, at least for hack and
    // maybe for the other examples, too
    void receivedMessage(
            ClientChannel channel, BigInteger sender, ByteBuffer message);

    void leftChannel(ClientChannel channel);
}
