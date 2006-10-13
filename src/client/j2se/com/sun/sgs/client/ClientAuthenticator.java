package com.sun.sgs.client;

public interface ClientAuthenticator {
    void passwordLogin(String login, byte[] password);
}
