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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

/**
 * An extension of {@link AsyncServerSocketChannelImpl}.
 * It's accept method returns the SSL variant
 * {@link AsyncSSLSocketchannelImpl}.
 */
class AsyncSSLServerSocketChannelImpl
    extends AsyncServerSocketChannelImpl
{
    /** The valid socket options for this channel. */
    private static final Set<SocketOption> socketOptions;
    static {
        Set<? extends SocketOption> es = EnumSet.of(
            StandardSocketOption.SO_RCVBUF,
            StandardSocketOption.SO_REUSEADDR);
        socketOptions = Collections.unmodifiableSet(es);
    }

    /**
     * Creates a new instance registered with the given channel group.
     * 
     * @param group the channel group
     * @throws IOException if an I/O error occurs
     */
    AsyncSSLServerSocketChannelImpl(AsyncGroupImpl group)
        throws IOException
    {
        super(group);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<AsynchronousSocketChannel, A> accept(
            A attachment,
            CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        return key.execute(
            OP_ACCEPT, attachment, handler, 0, TimeUnit.MILLISECONDS,
            new Callable<AsynchronousSocketChannel>() {
                public AsynchronousSocketChannel call() throws IOException {
                    try {
                        SocketChannel newChannel = channel.accept();
                        if (newChannel == null) {
                            // TODO re-execute on the key somehow? -JM
                            throw new IOException("accept failed");
                        }
                        return new AsyncSSLSocketChannelImpl(group, newChannel);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                } });
    }
}
