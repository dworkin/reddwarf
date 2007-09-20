package com.sun.sgs.impl.nio;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.CompletionHandler;

class AsyncOp<R>
    extends FutureTask<R>
{
    static final Logger log = Logger.getLogger(AsyncOp.class.getName());

    private final Runnable completionRunner;

    static <R, A> AsyncOp<R>
    create(A attachment,
           CompletionHandler<R, A> handler,
           Callable<R> callable)
    {
        return new AsyncOp<R>(attachment, handler, callable);
    }

    protected <A> AsyncOp(
            A attachment,
            CompletionHandler<R, A> handler,
            Callable<R> callable)
    {
        super(callable);
        completionRunner =
            (handler == null) ? null : getCompletion(handler, attachment, this);
    }

    protected static <R, A> Runnable
    getCompletion(final CompletionHandler<R, A> handler,
                  final A attachment,
                  final Future<R> future)
    {
        return new Runnable() {
            public void run() {
                handler.completed(AttachedFuture.wrap(future, attachment));
            }
        };
    }

    @Override
    protected void done() {
        log.log(Level.FINER, "done");
        if (completionRunner != null)
            completionRunner.run();
    }

    @Override
    public void setException(Throwable t) {
        super.setException(t);
    }
}
