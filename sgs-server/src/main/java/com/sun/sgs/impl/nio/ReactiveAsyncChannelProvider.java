/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
