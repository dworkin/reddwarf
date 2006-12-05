package com.sun.sgs.client.comm;

import java.io.IOException;

public interface ClientConnection {

    void sendMessage(byte[]  message);
    void disconnect() throws IOException;
}
