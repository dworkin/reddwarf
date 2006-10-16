package com.sun.sgs.client;

public interface ClientLogin {
    void sendLoginMessage(byte[] message);
    void disconnect();
}
