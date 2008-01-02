package com.sun.sgs.client.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public interface UtilChannelListener {

    void receivedMessage(
            UtilChannel channel, BigInteger sender, ByteBuffer message);

    void leftChannel(UtilChannel channel);
}
