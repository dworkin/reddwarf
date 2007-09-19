/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public final class DefaultAsyncChannelProvider {

    private DefaultAsyncChannelProvider() { } // no instantiation

    public static AsynchronousChannelProvider create() {
        return new ReactiveAsyncChannelProvider();
    }
}
