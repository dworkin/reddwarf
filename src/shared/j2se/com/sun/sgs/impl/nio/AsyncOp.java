package com.sun.sgs.impl.nio;

import java.util.concurrent.Callable;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

abstract class AsyncOp<R>
{
    private volatile boolean pending;
    boolean isPending() {
        return pending;
    }
    protected void alreadyPendingPolicy() { }
    protected <A> IoFuture<R, A> submit(Callable<R> callable, A attachment, CompletionHandler<R, ? super A> handler) {
        // TODO
        return null;
    }
}
