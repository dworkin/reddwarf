/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client.listener;

public interface DisconnectedListener {
    void disconnected(boolean graceful, String reason);
}
