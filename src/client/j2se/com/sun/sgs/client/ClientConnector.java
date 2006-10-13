package com.sun.sgs.client;

public interface ClientConnector {
    void connect(ClientAuthenticator auth, ServerSessionListener sessionListener);
}
