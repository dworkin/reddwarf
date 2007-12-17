package com.sun.sgs.client.util;

import java.math.BigInteger;

public interface UtilChannelListener {

    void receivedMessage(
            UtilChannel channel, BigInteger sender, byte[] message);

    void leftChannel(UtilChannel channel);
}
