package com.sun.sgs.client;

public interface ClientLoginListener {
    public byte[] loginMessageReceived(ClientAuthenticator authenticator, byte[] message);
}
