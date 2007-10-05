package com.sun.sgs.nio.channels;

/**
 * A handler for consuming the result of an asynchronous operation.
 * <p>
 * The asynchronous channels defined in this package allow a completion
 * handler to be specified to consume the result of an asynchronous
 * operation. When an operation completes the handler's
 * {@link #completed completed} method is invoked with the result.
 *
 * <h3>Usage Example</h3>
 * <pre>
 *    InetSocketAddress addr = ...
 *    AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
 * 
 *    ch.connect(addr, new CompletionHandler&lt;Void,Void&gt;() {
 *        public void completed(IoFuture&lt;Void,Void&gt; result) {
 *            try {  
 *                result.getNow();
 *                // connection established
 * 
 *            } catch (ExecutionException x) { 
 *                ...
 *            }
 *        }
 *    });
 * </pre>
 * 
 * @param <R> the result type
 * @param <A> the attachment type
 */
public interface CompletionHandler<R, A> {

    /**
     * Invoked when an operation has completed.
     * <p>
     * The {@code result} parameter is an {@link IoFuture} representing the
     * result of the operation. Its {@link IoFuture#getNow() getNow} method 
     * should be invoked to retrieve the result.
     * <p>
     * This method should complete in a timely manner so as to avoid keeping
     * this thread from dispatching to other completion handlers.
     * 
     * @param result the {@code IoFuture} representing the result of the
     *               operation
     */
    void completed(IoFuture<R, A> result);
}
