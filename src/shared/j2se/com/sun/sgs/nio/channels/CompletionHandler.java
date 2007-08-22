package com.sun.sgs.nio.channels;

/**
 * @param <R> the result type
 * @param <A> the attachment type
 */
public interface CompletionHandler<R, A> {

    /**
     * Invoked when an operation has completed.
     * <p>
     * The result parameter is an IoFuture representing the result of the
     * operation. Its getNow method should be invoked to retrieve the
     * result.
     * <p>
     * This method should complete in a timely manner so as to avoid keeping
     * this thread from dispatching to other completion handlers.
     *
     * @param result the IoFuture representing the result of the operation
     */
    void completed(IoFuture<R, A> result);
}
