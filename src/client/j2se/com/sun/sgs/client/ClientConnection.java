package com.sun.sgs.client;

import java.nio.ByteBuffer;

public interface ClientConnection {

    void sendAuthMessage(ByteBuffer message);
    void disconnect();
}
