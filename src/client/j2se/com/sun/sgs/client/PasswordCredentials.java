package com.sun.sgs.client;

public final class PasswordCredentials implements ClientCredentials {
    private final String username;
    private final byte[] password;

    public PasswordCredentials(String username, byte[] password) {
	this.username = username;
	this.password = password;
    }

    public String getUsername() { return username; }
    public byte[] getPassword() { return password; }
}
