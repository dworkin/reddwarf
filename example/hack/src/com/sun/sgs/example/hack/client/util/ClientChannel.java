package com.sun.sgs.example.hack.client.util;

import java.nio.ByteBuffer;

public interface ClientChannel {

    // FIXME need a dispatcher / manager to tell the client when it has
    // joined a channel

    String getName();
    void send(ByteBuffer message);
}
