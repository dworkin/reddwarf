package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

final class ChannelState implements ManagedObject, Serializable {

    final String name;
    final ChannelListener listener;
    final Delivery delivery;
    final Map<ClientSession, ChannelListener> sessions =
	new HashMap<ClientSession, ChannelListener>();
    
    ChannelState(String name, ChannelListener listener, Delivery delivery) {
	this.name = name;
	this.listener = listener;
	this.delivery = delivery;
    }
}
