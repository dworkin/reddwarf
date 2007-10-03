/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;

import com.sun.sgs.nio.channels.NetworkChannel;

/**
 * Common interface for {@code AsynchronousChannel} implementations.
 */
interface AsyncChannelImpl extends NetworkChannel {

    /**
     * Returns the {@code SelectableChannel} for this {@code AsyncChannelImpl}.
     *
     * @return the {@code SelectableChannel}
     */
    SelectableChannel channel();
}
