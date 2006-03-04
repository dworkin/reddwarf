package com.sun.gi.framework.interconnect.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportManager;

public class NullTransportManager implements TransportManager {

    private ConcurrentHashMap<String, NullTransportChannel> chanMap;

    public NullTransportManager() {
	chanMap = new ConcurrentHashMap<String, NullTransportChannel>();
    }

    public TransportChannel openChannel(String name) throws IOException {
	NullTransportChannel newChan = new NullTransportChannel(name);
	NullTransportChannel chan = chanMap.putIfAbsent(name, newChan);
	return (chan == null) ? newChan : chan;
    }
}
