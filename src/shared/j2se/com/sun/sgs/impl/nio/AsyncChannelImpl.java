/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

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

    /**
     * Notifies this channel that IO operations are ready.
     *
     * @param ops the ready ops.
     *
     * @see SelectionKey
     */
    void selected(int ops);

    /**
     * Notifies this channel that the given exception should be set on
     * the asynchronous task associated with {@code op}.
     *
     * @param op the operation that has had an exception
     * @param t the exception to set
     */
    void setException(int op, Throwable t);
}
