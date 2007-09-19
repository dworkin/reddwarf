package com.sun.sgs.impl.nio;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.CompletionHandler;

class AsyncOp<R>
    extends FutureTask<R>
    implements Delayed
{
    private final SelectableChannel channel;
    private final int op;
    private final long timeout;
    private final TimeUnit unit;
    private final Runnable completionRunner;

    static <R, A> AsyncOp<R>
    create(SelectableChannel channel,
           int op,
           A attachment,
           CompletionHandler<R, A> handler,
           Callable<R> callable)
    {
        return create(channel, op, 0, TimeUnit.MILLISECONDS,
                      attachment, handler, callable);
    }

    static <R, A> AsyncOp<R>
    create(SelectableChannel channel,
           int op,
           long timeout,
           TimeUnit unit,
           A attachment,
           CompletionHandler<R, A> handler,
           Callable<R> callable)
    {
        return new AsyncOp<R>(
            channel, op, timeout, unit, attachment, handler, callable);
    }

    protected <A> AsyncOp(
            SelectableChannel channel,
            int op,
            long timeout,
            TimeUnit unit,
            A attachment,
            CompletionHandler<R, A> handler,
            Callable<R> callable)
    {
        super(callable);
        this.channel = channel;
        this.op = op;
        this.timeout = timeout;
        this.unit = unit;
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
        if (completionRunner != null)
            completionRunner.run();
    }

    @Override
    public void setException(Throwable t) {
        super.setException(t);
    }

    SelectableChannel getChannel() {
        return channel;
    }

    int getOp() {
        return op;
    }

    public long getDelay(TimeUnit unit) {
        return unit.convert(timeout, this.unit);
    }

    public int compareTo(Delayed o) {
        final long other = o.getDelay(unit);
        return (timeout<other ? -1 : (timeout==other ? 0 : 1));
    }
}
