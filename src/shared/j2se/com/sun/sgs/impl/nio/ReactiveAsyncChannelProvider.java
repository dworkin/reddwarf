/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * A select-based asynchronous IO provider.
 */
public class ReactiveAsyncChannelProvider extends AsyncProviderImpl {

    /**
     * Constructs an instance of this class.  Public visibility to allow
     * instantiation from a property at runtime.
     */
    ReactiveAsyncChannelProvider() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    public ReactiveChannelGroup
    openAsynchronousChannelGroup(ExecutorService executor)
        throws IOException
    {
        return new ReactiveChannelGroup(this, executor);
    }
}
