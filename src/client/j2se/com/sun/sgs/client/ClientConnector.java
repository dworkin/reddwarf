package com.sun.sgs.client;

import java.util.concurrent.Future;

public interface ClientConnector {
    Future<ServerSession> connect(
	    ClientLoginListener loginListener,
	    ServerSessionListener sessionListener);
}
