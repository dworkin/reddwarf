/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.Closeable;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

/**
 * TODO doc
 */
interface AsyncKey
    extends Closeable, Executor
{
    /**
     * TODO doc
     * @return the channel
     */
    SelectableChannel channel();
    
    /**
     * TODO doc
     * @param op
     * @return true if the operation is pending
     */
    boolean isOpPending(int op);
    
    /**
     * TODO doc
     * @param ops
     */
    void selected(int ops);

    /**
     * TODO doc
     * @param <R>
     * @param <A>
     * @param op
     * @param attachment
     * @param handler
     * @param timeout
     * @param unit
     * @param callable
     * @return an IoFuture
     */
    <R, A> IoFuture<R, A>
    execute(int op,
            A attachment,
            CompletionHandler<R, ? super A> handler,
            long timeout,
            TimeUnit unit,
            Callable<R> callable);


}
