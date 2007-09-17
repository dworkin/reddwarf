package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;

abstract class AsyncOp<R, A, T extends SelectableChannel>
    extends FutureTask<R>
    implements IoFuture<R, A>
{
    private final T channel;
    private final int op;
    private final long timeout;
    private final TimeUnit unit;
    private A attachment;
    private final Runnable completionRunner;

    AsyncOp(T channel,
            int op,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<R, ? super A> handler,
            Callable<R> callable)
    {
        super(callable);
        this.channel = channel;
        this.op = op;
        this.timeout = timeout;
        this.unit = unit;
        this.attachment = attachment;
        completionRunner =
            (handler == null) ? null : getCompletion(handler, attachment);
    }

    protected <R, A> Runnable
    getCompletion(final CompletionHandler<R, A> handler,
                  final A attachment)
    {
        return new Runnable() {
            public void run() {
                handler.completed(AttachedFuture.wrap(future, attachment));
            }
        };
    }

    static final class CompletionRunner() {
        final CompletionHandler<R, A> handler;
        final A attachment;

        void completed(Future<R> future) {
            handler.completed(AttachedFuture.wrap(future, attachment));
        }
    }
}
