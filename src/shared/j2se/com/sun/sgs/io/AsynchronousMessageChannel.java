/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.io;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.impl.nio.AttachedFuture;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;

/**
 * An attempt at making a wrapper channel.
 */
public class AsynchronousMessageChannel implements Channel {

    /** The logger for this class. */
    static final Logger log =
        Logger.getLogger(AsynchronousMessageChannel.class.getName());

    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...)
     */
    final AsynchronousByteChannel channel;

    final AtomicBoolean readPending = new AtomicBoolean();
    final AtomicBoolean writePending = new AtomicBoolean();

    private final CompleteMessageDetector detector;

    /**
     * Creates a new instance of this class with the given channel.
     * Uses a PrefixMessageDetector on a 4-byte length field to
     * determine when a message is complete.
     * 
     * @param channel a channel
     */
    public AsynchronousMessageChannel(AsynchronousByteChannel channel)
    {
        this(channel, new PrefixMessageLengthDetector(4));
    }

    /**
     * Creates a new instance of this class with the given channel.
     * 
     * @param channel a channel
     * @param detector the CompleteMessageDetector
     */
    public AsynchronousMessageChannel(AsynchronousByteChannel channel,
                                      CompleteMessageDetector detector)
    {
        this.channel = channel;
        this.detector = detector;
    }

    int completeMessageLength(ByteBuffer buf) {
        return detector.completeMessageLength(buf);
    }

    /**
     * TODO doc
     */
    public interface CompleteMessageDetector {
        /**
         * 
         * @param buf the buffer
         * @return the length, or -1
         */
        int completeMessageLength(ByteBuffer buf);
    }

    /**
     * TODO doc
     */
    public static class PartialMessageDetector
        implements CompleteMessageDetector
    {
        /** {@inheritDoc} */
        public int completeMessageLength(ByteBuffer buf) {
            int pos = buf.position();
            return pos > 0 ? pos : -1;
        }
    }

    /**
     * TODO doc
     */
    public static class PrefixMessageLengthDetector
        implements CompleteMessageDetector
    {
        private final int prefixLength;

        /**
         * @param n the prefix length, in bytes
         */
        public PrefixMessageLengthDetector(int n) {
            if (! (n == 1 || n == 2 || n == 4))
                throw new IllegalArgumentException("bad prefixLength");

            this.prefixLength = n;
        }

        /** {@inheritDoc} */
        public int completeMessageLength(ByteBuffer buf) {
            if (buf.position() >= prefixLength) {
                return peekPrefixLength(buf) + prefixLength;
            }

            // Check that there is room for the prefix in the buffer
            if (buf.limit() < prefixLength)
                throw new BufferOverflowException();

            return -1;
        }

        private int peekPrefixLength(ByteBuffer buf) {
            switch (prefixLength) {
            case 1:
                return buf.get(0) & 0xFF;
            case 2:
                return buf.getShort(0) & 0xFFFF;
            case 4:
                return buf.getInt(0);
            default:
                // Shouldn't be possible
                throw new IllegalStateException("bad prefixLength");
            }
        }
    }

    /**
     * Result is a read-only view into the "dst" buffer containing
     * a complete message as determiend by the MessageDispatcher.
     * @param dst 
     * @param attachment 
     * @param handler 
     * @param <A> 
     * @return an IoFuture
     * @throws ReadPendingException if a read is in progress
     */
    public <A> IoFuture<ByteBuffer, A>
    read(ByteBuffer dst,
         A attachment,
         CompletionHandler<ByteBuffer, ? super A> handler)
    {
        if (! readPending.compareAndSet(false, true))
            throw new ReadPendingException();

        return AttachedFuture.wrap(
            new Reader<A>(attachment, handler).start(dst), attachment);
    }

    /**
     * @param <A>
     * @param dst
     * @param handler
     * @return an IoFuture
     * @throws ReadPendingException if a read is in progress
     */
    public final <A> IoFuture<ByteBuffer, A>
    read(ByteBuffer dst, CompletionHandler<ByteBuffer, ? super A> handler) {
        return read(dst, null, handler);
    }

    /**
     * @param <A>
     * @param src
     * @param attachment
     * @param handler
     * @return an IoFuture
     * @throws WritePendingException if a write is in progress
     */
    public <A> IoFuture<Void, A>
    write(ByteBuffer src, A attachment,
          CompletionHandler<Void, ? super A> handler)
    {
        if (! writePending.compareAndSet(false, true))
            throw new WritePendingException();

        return AttachedFuture.wrap(
            new Writer<A>(attachment, handler).start(src), attachment);
    }

    /**
     * @param <A>
     * @param src
     * @param handler
     * @return an IoFuture
     * @throws WritePendingException if a write is in progress
     */
    public final <A> IoFuture<Void, A>
    write(ByteBuffer src, CompletionHandler<Void, ? super A> handler) {
        return write(src, null, handler);
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        channel.close();
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return channel.isOpen();
    }

    // Implementation details

    static final Runnable emptyRunner =
        new Runnable() { public void run() {} };

    static <R, A> Completer<R, A>
    getCompleter(CompletionHandler<R, A> handler, A attachment) {
        if (handler == null)
            return null;
        return new Completer<R, A>(handler, attachment);
    }

    static class Completer<R, A> {
        private CompletionHandler<R, A> handler;
        private A attachment;

        Completer(CompletionHandler<R, A> handler, A attachment) {
            this.handler = handler;
            this.attachment = attachment;
        }

        void run(Future<R> future) {
            final CompletionHandler<R, A> h = handler;
            final A att = attachment;
            handler = null;
            attachment = null;
            h.completed(AttachedFuture.wrap(future, att));
        }
    }

    static abstract class Wrapper<V, R, A>
        extends FutureTask<V>
        implements CompletionHandler<R, A>
    {
        private final Object lock;
        private final Completer<V, ?> completionRunner;

        // @GuardedBy("lock")
        private IoFuture<R, A> currentFuture;

        <B> Wrapper(B attachment,
                    CompletionHandler<V, ? super B> handler)
        {
            super(emptyRunner, null);

            lock = new Object();
            completionRunner = getCompleter(handler, attachment);
        }

        Wrapper<V, R, A> start(A x) {
            synchronized (lock) {
                log.log(Level.FINE, "{0}, Calling implStart", this);
                currentFuture = implStart(x);
                return this;
            }
        }

        /**
         * @param attachment
         * @return the new future, or {@code} null if finished
         */
        abstract protected IoFuture<R, A>
        implStart(A attachment);

        /**
         * @param result
         * @return the new future, or {@code} null if finished
         * @throws Exception
         */
        abstract protected IoFuture<R, A>
        implCompleted(IoFuture<R, A> result) throws Exception;

        /** {@inheritDoc} */
        public void completed(IoFuture<R, A> result) {
            synchronized (lock) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE,
                        "{0} pre currentFuture is {1} ",
                        new Object[] { this, currentFuture });
                }
                
                try {
                    currentFuture = implCompleted(result);
                } catch (ExecutionException e) {
                    setException(e.getCause());
                } catch (Throwable t) {
                    setException(t);
                }
                
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE,
                        "{0} post currentFuture is {1} ",
                        new Object[] { this, currentFuture });
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            log.log(Level.FINE, "{0} done, calling completion", this);
            currentFuture = null;
            if (completionRunner != null)
                completionRunner.run(this);
        }

        /** {@inheritDoc} */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (lock) {
                if (isDone())
                    return false;

                boolean success = currentFuture.cancel(mayInterruptIfRunning);
                if (success) {
                    // Cancel this wrapper, too
                    success = super.cancel(false);

                    // Wrapper should always be cancellable if it's not done
                    assert success;
                }

                return success;
            }
        }
    }

    final class Reader<A>
        extends Wrapper<ByteBuffer, Integer, ByteBuffer>
    {
        private int messageLen = -1;

        Reader(A attachment,
               CompletionHandler<ByteBuffer, ? super A> handler)
        {
            super(attachment, handler);
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            readPending.set(false);
            super.done();
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implStart(ByteBuffer dst) {
            return processBuffer(dst);
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implCompleted(IoFuture<Integer, ByteBuffer> result)
            throws ExecutionException 
        {
            ByteBuffer dst = result.attach(null);
            int bytesRead = result.getNow();

            if (bytesRead < 0) {
                set(null);
                return null;
            }

            return processBuffer(dst);
        }

        private IoFuture<Integer, ByteBuffer>
        processBuffer(ByteBuffer dst) {
            ByteBuffer readBuf = dst.asReadOnlyBuffer();

            if (messageLen < 0) {
                messageLen = completeMessageLength(dst);

                if (messageLen >= 0) {
                    // Ensure that the buffer will hold the complete message
                    if (dst.limit() < messageLen) {
                        throw new BufferOverflowException();
                    }
                } else {
                    // Or at least ensure that the buffer isn't full
                    if (! dst.hasRemaining())
                        throw new BufferOverflowException();
                }
            }

            if (messageLen >= 0 && dst.position() >= messageLen) {
                if (log.isLoggable(Level.FINER)) {
                    log.log(Level.FINER,
                            "{0} read complete {1}:{2}",
                            new Object[] {
                                this, messageLen, dst.position()
                            });
                }
                readBuf.limit(messageLen).flip();
                set(readBuf); // Invokes the completion handler
                return null;
            }

            if (log.isLoggable(Level.FINER)) {
                log.log(Level.FINER,
                        "{0} read incomplete {1}:{2}",
                        new Object[] {
                            this, messageLen, dst.position()
                        });
            }
            return channel.read(dst, dst, this);
        }
    }

    final class Writer<A>
        extends Wrapper<Void, Integer, ByteBuffer>
    {
        Writer(A attachment,
               CompletionHandler<Void, ? super A> handler)
        {
            super(attachment, handler);
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            writePending.set(false);
            super.done();
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implStart(ByteBuffer src) {
            return channel.write(src, src, this);
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implCompleted(IoFuture<Integer, ByteBuffer> result)
            throws ExecutionException
        {
            ByteBuffer src = result.attach(null);
            result.getNow();
            if (src.hasRemaining()) {
                // Write some more
                return channel.write(src, src, this);
            } else {
                // Finished
                set(null);
                return null;
            }
        }
    }
}
