package com.sun.sgs.nio.channels;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * Utility methods for channels and streams.
 * <p>
 * This class defines:
 * <ul>
 * <li>Static methods that support the interoperation of the stream
 *     classes of the java.io package with the channel classes of
 *     this package.
 * <li>Method to get the management interfaces for pools of channels
 *     in the the Java virtual machine.
 * </ul>
 */
public final class Channels {

    private Channels() { }              // No instantiation

    private static void writeAll(AsynchronousByteChannel ch, ByteBuffer bb)
        throws IOException
    {
        try {
            while (bb.hasRemaining()) {
                ch.write(bb, null).get();
            }
        } catch (InterruptedException e) {
            ch.close();
            Thread.currentThread().interrupt();
            throw new ClosedByInterruptException();
        } catch (ExecutionException e) {
            launderExecutionException(e);
        }
    }

    private static int launderExecutionException(ExecutionException e)
        throws IOException
    {
        Throwable t = e.getCause();
        if (t instanceof IOException)
            throw (IOException) t;
        else if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else
            throw new IOError(t);
    }

    /**
     * Constructs a stream that reads bytes from the given channel.
     * <p>
     * The stream will not be buffered, and it will not support the
     * {@code mark} or {@code reset} methods. The stream will be safe for
     * access by multiple concurrent threads. Closing the stream will in
     * turn cause the channel to be closed.
     * 
     * @param ch the channel from which bytes will be read
     * @return a new input stream
     */
    public static InputStream newInputStream(final AsynchronousByteChannel ch)
    {
        return new InputStream() {

            private ByteBuffer bb = null;
            private byte[] bs = null;       // Invoker's previous array
            private byte[] b1 = null;

            @Override
            public synchronized int read() throws IOException {
               if (b1 == null)
                    b1 = new byte[1];
               if (this.read(b1) == -1)
                   return -1;
               return b1[0];
            }

            @Override
            public synchronized int read(byte[] bs, int off, int len)
                throws IOException
            {
                if ((off < 0) || (off > bs.length) || (len < 0) ||
                        ((off + len) > bs.length) || ((off + len) < 0)) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return 0;
                }
                ByteBuffer bb = ((this.bs == bs)
                                 ? this.bb
                                 : ByteBuffer.wrap(bs));
                bb.limit(Math.min(off + len, bb.capacity()));
                bb.position(off);
                this.bb = bb;
                this.bs = bs;
                try {
                    return ch.read(bb, null).get();
                } catch (InterruptedException e) {
                    ch.close();
                    Thread.currentThread().interrupt();
                    throw new ClosedByInterruptException();
                } catch (ExecutionException e) {
                    return launderExecutionException(e);
                }
            }

            @Override
            public void close() throws IOException {
                ch.close();
            }
        };
    }

    /**
     * Constructs a stream that writes bytes to the given channel.
     * <p>
     * The stream will not be buffered. The stream will be safe for access
     * by multiple concurrent threads. Closing the stream will in turn cause
     * the channel to be closed.
     *
     * @param ch the channel to which bytes will be written
     * @return a new output stream
     */
    public static OutputStream newOutputStream(
                                    final AsynchronousByteChannel ch)
    {
        return new OutputStream() {

            private ByteBuffer bb = null;
            private byte[] bs = null;       // Invoker's previous array
            private byte[] b1 = null;

            @Override
            public synchronized void write(int b) throws IOException {
               if (b1 == null)
                    b1 = new byte[1];
                b1[0] = (byte)b;
                this.write(b1);
            }

            @Override
            public synchronized void write(byte[] bs, int off, int len)
                throws IOException
            {
                if ((off < 0) || (off > bs.length) || (len < 0) ||
                    ((off + len) > bs.length) || ((off + len) < 0)) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return;
                }
                ByteBuffer bb = ((this.bs == bs)
                                 ? this.bb
                                 : ByteBuffer.wrap(bs));
                bb.limit(Math.min(off + len, bb.capacity()));
                bb.position(off);
                this.bb = bb;
                this.bs = bs;
                Channels.writeAll(ch, bb);
            }

            @Override
            public void close() throws IOException {
                ch.close();
            }
        };
    }

    /**
     * Returns a list of the {@link ChannelPoolMXBean} objects in the Java
     * virtual machine.
     * <p>
     * The list of {@code ChannelPoolMXBean} objects returned by this method
     * is an aggregation of the {@code ChannelPoolMXBean} objects obtained
     * from:
     * <ul>
     * <li>The system-wide default {@link SelectorProvider} if it
     * implements the {@link ManagedChannelFactory} interface.
     * <li>The system-wide default {@link AsynchronousChannelProvider} if
     * it implements the {@code ManagedChannelFactory} interface.
     * </ul>
     * The list of {@code ChannelPoolMXBeans} is returned in no
     * particularorder, and the ordering may differ from one invocation to
     * the next. Whether the list is modifiable is implementation specific.
     *
     * @return a list of {@code ChannelPoolMXBean} objects
     */
    public static List<ChannelPoolMXBean> getChannelPoolMXBeans() {
        // TODO
        return Collections.emptyList();
    }

    public static ReadableByteChannel newChannel(InputStream in) {
        return java.nio.channels.Channels.newChannel(in);
    }

    public static WritableByteChannel newChannel(OutputStream out) {
        return java.nio.channels.Channels.newChannel(out);
    }

    public static InputStream newInputStream(ReadableByteChannel ch) {
        return java.nio.channels.Channels.newInputStream(ch);
    }

    public static OutputStream newOutputStream(WritableByteChannel ch) {
        return java.nio.channels.Channels.newOutputStream(ch);
    }
 
    public static Reader newReader(ReadableByteChannel ch,
                                   CharsetDecoder dec,
                                   int minBufferCap)
    {
        return java.nio.channels.Channels.newReader(ch, dec, minBufferCap);
    }

    public static Reader newReader(ReadableByteChannel ch, String csName) {
        return java.nio.channels.Channels.newReader(ch, csName);
    }
 
    public static Writer newWriter(WritableByteChannel ch,
                                   CharsetEncoder enc,
                                   int minBufferCap) {
        return java.nio.channels.Channels.newWriter(ch, enc, minBufferCap);
    }

    public static Writer newWriter(WritableByteChannel ch, String csName) {
        return java.nio.channels.Channels.newWriter(ch, csName);
    }
}
