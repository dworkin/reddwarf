/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * TODO doc
 */
public interface DispatchListener {

    /**
     * TODO doc
     * 
     * @param message a message
     */
    void dispatch(ByteBuffer message);

    /**
     * TODO doc
     * TODO is this method necessary?
     */
    void disconnected(boolean graceful);
}
