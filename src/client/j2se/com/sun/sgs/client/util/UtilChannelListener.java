package com.sun.sgs.client.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public interface UtilChannelListener {

    // N.B. needs to pass {@code null} for the
    // server's sender id, at least for hack and
    // maybe for the other examples, too
    void receivedMessage(
            UtilChannel channel, BigInteger sender, ByteBuffer message);

    void leftChannel(UtilChannel channel);
}
