package com.sun.gi.framework.interconnect.impl;

import java.nio.ByteBuffer;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;

public class NullTransportChannel implements TransportChannel {

    private final String name;

    NullTransportChannel(String channelName) {
	name = channelName;
    }

    public String getName() {
	return name;
    }

    public void sendData(ByteBuffer data) {
	// no-op
    }

    public void sendData(ByteBuffer[] bufs) {
	// no-op
    }

    public void doRecieveData(ByteBuffer data) {
	// no-op
    }

    public void addListener(TransportChannelListener l) {
	// no-op
    }

    public void removeListener(TransportChannelListener l) {
	// no-op
    }

    public void close() {
	// no-op
    }

    public void closeChannel() {
	// no-op
    }
}
