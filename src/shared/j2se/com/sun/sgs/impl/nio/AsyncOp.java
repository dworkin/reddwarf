package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.nio.channels.CompletionHandler;

class AsyncOp {
    private final AtomicBoolean pending = new AtomicBoolean();
    private Runnable completionRunner = null;
    private final SelectableChannel channel;
    private final int op;

    AsyncOp(SelectableChannel channel, int op) {
        this.channel = channel;
        this.op = op;
    }

    SelectableChannel getChannel() {
        return channel;
    }

    int getOp() {
        return op;
    }

    boolean isPending() {
        return pending.get();
    }

    // override this with exception-throwing code, if desired
    protected boolean preTask() {
        return pending.compareAndSet(false, true);
    }

    protected void postTask() {
        pending.set(false);
    }

    void done() {
        final Runnable runner = completionRunner;
        completionRunner = null;
        postTask();
        if (runner != null)
            runner.run();
    }

    <R, A> RunnableFuture<R>
    submit(A attachment,
           CompletionHandler<R, ? super A> handler,
           Callable<R> callable)
    {
        preTask();
        AsyncOpTask<R> task = new AsyncOpTask<R>(callable);
        completionRunner = getCompletionRunner(task, attachment, handler);
        return task;
    }

    static private <R, A> Runnable
    getCompletionRunner(final Future<R> future,
                   final A attachment,
                   final CompletionHandler<R, A> handler)
    {
        if (handler == null)
            return null;

        return new Runnable() {
            public void run() {
                handler.completed(AttachedFuture.wrap(future, attachment));
            }
        };
    }

    final class AsyncOpTask<R> extends FutureTask<R> {
        AsyncOpTask(Callable<R> callable) {
            super(callable);
        }

        @Override
        protected void done() {
            AsyncOp.this.done();
        }
    }
}
