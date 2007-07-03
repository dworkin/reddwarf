/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client.listener;

import com.sun.sgs.client.SessionId;

public interface ChannelMessageListener {
    void receivedMessage(String channelName, SessionId sender, byte[] message);
}
