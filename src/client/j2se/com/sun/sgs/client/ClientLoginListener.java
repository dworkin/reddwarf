package com.sun.sgs.client;

public interface ClientLoginListener {
    public void loginMessageReceived(ClientLogin authenticator, byte[] message);
    public void loginFailed(String reason);
}
