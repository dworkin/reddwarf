/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * Factory for obtaining the default {@code AsynchronousChannelProvider}.
 * Used by {@link AsynchronousChannelProvider#provider()}.
 */
public final class DefaultAsyncChannelProvider {

    private DefaultAsyncChannelProvider() { } // no instantiation

    /**
     * Returns the system-default {@code AsynchronousChannelProvider}.
     *
     * @return the system-default {@code AsynchronousChannelProvider}
     */
    public static AsynchronousChannelProvider create() {
        return new ReactiveAsyncChannelProvider();
    }
}
