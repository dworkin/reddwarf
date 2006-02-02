package com.sun.gi.comm.users.protocol;

import java.nio.ByteBuffer;

public interface TransportProtocolTransmitter {

    public void sendBuffers(ByteBuffer[] buffs, boolean reliable);

    public void closeConnection();
}
