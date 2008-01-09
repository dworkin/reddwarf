package com.sun.sgs.client.util;

import java.nio.ByteBuffer;

public interface UtilChannel {

    // FIXME need a dispatcher / manager to tell the client when it has
    // joined a channel

    String getName();
    void send(ByteBuffer message);
}
