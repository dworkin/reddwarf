package com.sun.sgs.impl.client.comm;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ClientConnection {

    void sendMessage(byte[]  message);
    void disconnect() throws IOException;
}
