package com.sun.sgs.client.comm;

import java.nio.ByteBuffer;

public interface ClientConnection {

    void sendAuthMessage(ByteBuffer message);
    void disconnect();
}
