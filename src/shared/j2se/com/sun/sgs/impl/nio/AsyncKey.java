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

interface AsyncKey<T extends SelectableChannel>
    extends Closeable, Executor
{
    /**
     * @return the channel
     */
    T channel();
    
    /**
     * @param op
     * @return true if the operation is pending
     */
    boolean isOpPending(int op);
    
    /**
     * @param ops
     */
    void selected(int ops);


    /**
     * @param <R>
     * @param <A>
     * @param op
     * @param attachment
     * @param handler
     * @param callable
     * @return an IoFuture
     */
    <R, A> IoFuture<R, A>
    execute(int op,
            A attachment,
            CompletionHandler<R, ? super A> handler,
            Callable<R> callable);

    /**
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
