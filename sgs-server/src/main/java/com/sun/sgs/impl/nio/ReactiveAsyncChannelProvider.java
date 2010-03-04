/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ExecutorService;

/**
 * A select-based asynchronous IO provider.
 */
public class ReactiveAsyncChannelProvider extends AsyncProviderImpl {

    /**
     * Creates an asynchronous channel provider using the system default
     * {@link SelectorProvider}.  Public visibility to allow
     * instantiation from a property at runtime.
     * 
     * @see SelectorProvider#provider()
     */
    public ReactiveAsyncChannelProvider() {
        this(null);
    }

    /**
     * Creates an asynchronous channel provider using the given
     * {@link SelectorProvider}. If the parameter is {@code null}, the
     * system default {@code SelectorProvider} will be used.
     * 
     * @param selProvider the {@code SelectorProvider}, or {@code null} to
     *        use the system default {@code SelectorProvider}
     * 
     * @see SelectorProvider#provider()
     */
    protected ReactiveAsyncChannelProvider(SelectorProvider selProvider) {
        super(selProvider);
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
