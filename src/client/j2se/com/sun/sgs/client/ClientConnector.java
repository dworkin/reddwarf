package com.sun.sgs.client;

public interface ClientConnector {
    void connect(ServerSessionListener sessionListener);
    void cancel();
}
