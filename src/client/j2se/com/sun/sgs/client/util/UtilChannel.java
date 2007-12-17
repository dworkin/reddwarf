package com.sun.sgs.client.util;

public interface UtilChannel {

    // FIXME need a dispatcher / manager to tell the client when it has
    // joined a channel

    String getName();
    void send(byte[] message);
}
