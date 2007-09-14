package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;

abstract class AsyncOp {

    abstract <R, A> IoFuture<R, A>
    submit(A attachment,
           CompletionHandler<R, ? super A> handler,
           long timeout,
           TimeUnit unit);

    abstract boolean isPending();
}
