package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.LinkedList;
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

    /** Prevents instantiation of this class. */
    private Channels() { }

    // Based on code in java.nio.channels.Channels

    /**
     * Synchronously writes the contents of the buffer to the channel,
     * blocking until the entire buffer is written.
     * 
     * @param ch the channel to write to
     * @param bb the buffer to write to the channel
     * @throws IOException if an I/O error occurs
     * 
     */
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

    /**
     * Unwraps an {@code ExecutionException} and throws its cause.
     * 
     * @param e the {@code ExecutionException} to unwrap
     * @return a dummy int, since this function always throws an exception
     * 
     * @throws IOException if the cause was an {@code IOException}
     * @throws RuntimeException otherwise
     */
    private static int launderExecutionException(ExecutionException e)
        throws IOException
    {
        Throwable t = e.getCause();
        if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            // TODO is there something more appropriate to throw? -JM
            IOException ex = new IOException();
            ex.initCause(t);
            throw ex;
        }
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
        // Based on code in java.nio.channels.Channels

        return new InputStream() {

            private ByteBuffer buf = null;
            private byte[] prevBytes = null;       // Invoker's previous array
            private byte[] oneByte = null;

            @Override
            public synchronized int read() throws IOException {
               if (oneByte == null)
                    oneByte = new byte[1];
               if (this.read(oneByte) == -1)
                   return -1;
               return oneByte[0];
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
                ByteBuffer bb = ((this.prevBytes == bs)
                                 ? this.buf
                                 : ByteBuffer.wrap(bs));
                bb.limit(Math.min(off + len, bb.capacity()));
                bb.position(off);
                this.buf = bb;
                this.prevBytes = bs;
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

            // Based on code in java.nio.channels.Channels

            private ByteBuffer buf = null;
            private byte[] prevBytes = null;       // Invoker's previous array
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
                ByteBuffer bb = ((this.prevBytes == bs)
                                 ? this.buf
                                 : ByteBuffer.wrap(bs));
                bb.limit(Math.min(off + len, bb.capacity()));
                bb.position(off);
                this.buf = bb;
                this.prevBytes = bs;
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
     * particular order, and the ordering may differ from one invocation to
     * the next. Whether the list is modifiable is implementation specific.
     *
     * @return a list of {@code ChannelPoolMXBean} objects
     */
    public static List<ChannelPoolMXBean> getChannelPoolMXBeans() {
        List<ChannelPoolMXBean> result = new LinkedList<ChannelPoolMXBean>();

        List<Object> providers = Arrays.asList(new Object[] {
            SelectorProvider.provider(),
            AsynchronousChannelProvider.provider()
        });

        for (Object provider : providers) {
            if (provider instanceof ManagedChannelFactory) {
                result.addAll(
                    ((ManagedChannelFactory)provider).getChannelPoolMXBeans());
            }
        }

        return result;
    }

    /**
     * Constructs a channel that reads bytes from the given stream.
     *
     * <p> The resulting channel will not be buffered; it will simply redirect
     * its I/O operations to the given stream.  Closing the channel will in
     * turn cause the stream to be closed.  </p>
     *
     * @param  in
     *         The stream from which bytes are to be read
     *
     * @return  A new readable byte channel
     */
    public static ReadableByteChannel newChannel(InputStream in) {
        return java.nio.channels.Channels.newChannel(in);
    }

    /**
     * Constructs a channel that writes bytes to the given stream.
     *
     * <p> The resulting channel will not be buffered; it will simply redirect
     * its I/O operations to the given stream.  Closing the channel will in
     * turn cause the stream to be closed.  </p>
     *
     * @param  out
     *         The stream to which bytes are to be written
     *
     * @return  A new writable byte channel
     */
    public static WritableByteChannel newChannel(OutputStream out) {
        return java.nio.channels.Channels.newChannel(out);
    }

    /**
     * Constructs a stream that reads bytes from the given channel.
     *
     * <p> The <tt>read</tt> methods of the resulting stream will throw an
     * {@link IllegalBlockingModeException} if invoked while the underlying
     * channel is in non-blocking mode.  The stream will not be buffered, and
     * it will not support the {@link InputStream#mark mark} or {@link
     * InputStream#reset reset} methods.  The stream will be safe for access by
     * multiple concurrent threads.  Closing the stream will in turn cause the
     * channel to be closed.  </p>
     *
     * @param  ch
     *         The channel from which bytes will be read
     *
     * @return  A new input stream
     */
    public static InputStream newInputStream(ReadableByteChannel ch) {
        return java.nio.channels.Channels.newInputStream(ch);
    }

    /**
     * Constructs a stream that writes bytes to the given channel.
     *
     * <p> The <tt>write</tt> methods of the resulting stream will throw an
     * {@link IllegalBlockingModeException} if invoked while the underlying
     * channel is in non-blocking mode.  The stream will not be buffered.  The
     * stream will be safe for access by multiple concurrent threads.  Closing
     * the stream will in turn cause the channel to be closed.  </p>
     *
     * @param  ch
     *         The channel to which bytes will be written
     *
     * @return  A new output stream
     */
    public static OutputStream newOutputStream(WritableByteChannel ch) {
        return java.nio.channels.Channels.newOutputStream(ch);
    }

    /**
     * Constructs a reader that decodes bytes from the given channel using the
     * given decoder.
     *
     * <p> The resulting stream will contain an internal input buffer of at
     * least <tt>minBufferCap</tt> bytes.  The stream's <tt>read</tt> methods
     * will, as needed, fill the buffer by reading bytes from the underlying
     * channel; if the channel is in non-blocking mode when bytes are to be
     * read then an {@link IllegalBlockingModeException} will be thrown.  The
     * resulting stream will not otherwise be buffered, and it will not support
     * the {@link Reader#mark mark} or {@link Reader#reset reset} methods.
     * Closing the stream will in turn cause the channel to be closed.  </p>
     *
     * @param  ch
     *         The channel from which bytes will be read
     *
     * @param  dec
     *         The charset decoder to be used
     *
     * @param  minBufferCap
     *         The minimum capacity of the internal byte buffer,
     *         or <tt>-1</tt> if an implementation-dependent
     *         default capacity is to be used
     *
     * @return  A new reader
     */
    public static Reader newReader(ReadableByteChannel ch,
                                   CharsetDecoder dec,
                                   int minBufferCap)
    {
        return java.nio.channels.Channels.newReader(ch, dec, minBufferCap);
    }

    /**
     * Constructs a reader that decodes bytes from the given channel according
     * to the named charset.
     *
     * <p> An invocation of this method of the form
     *
     * <blockquote><pre>
     * Channels.newReader(ch, csname)</pre></blockquote>
     *
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>
     * Channels.newReader(ch,
     *                    Charset.forName(csName)
     *                        .newDecoder(),
     *                    -1);</pre></blockquote>
     *
     * @param  ch
     *         The channel from which bytes will be read
     *
     * @param  csName
     *         The name of the charset to be used
     *
     * @return  A new reader
     *
     * @throws  UnsupportedCharsetException
     *          If no support for the named charset is available
     *          in this instance of the Java virtual machine
     */
    public static Reader newReader(ReadableByteChannel ch, String csName) {
        return java.nio.channels.Channels.newReader(ch, csName);
    }

    /**
     * Constructs a writer that encodes characters using the given encoder and
     * writes the resulting bytes to the given channel.
     *
     * <p> The resulting stream will contain an internal output buffer of at
     * least <tt>minBufferCap</tt> bytes.  The stream's <tt>write</tt> methods
     * will, as needed, flush the buffer by writing bytes to the underlying
     * channel; if the channel is in non-blocking mode when bytes are to be
     * written then an {@link IllegalBlockingModeException} will be thrown.
     * The resulting stream will not otherwise be buffered.  Closing the stream
     * will in turn cause the channel to be closed.  </p>
     *
     * @param  ch
     *         The channel to which bytes will be written
     *
     * @param  enc
     *         The charset encoder to be used
     *
     * @param  minBufferCap
     *         The minimum capacity of the internal byte buffer,
     *         or <tt>-1</tt> if an implementation-dependent
     *         default capacity is to be used
     *
     * @return  A new writer
     */
    public static Writer newWriter(WritableByteChannel ch,
                                   CharsetEncoder enc,
                                   int minBufferCap) {
        return java.nio.channels.Channels.newWriter(ch, enc, minBufferCap);
    }

    /**
     * Constructs a writer that encodes characters according to the named
     * charset and writes the resulting bytes to the given channel.
     *
     * <p> An invocation of this method of the form
     *
     * <blockquote><pre>
     * Channels.newWriter(ch, csname)</pre></blockquote>
     *
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>
     * Channels.newWriter(ch,
     *                    Charset.forName(csName)
     *                        .newEncoder(),
     *                    -1);</pre></blockquote>
     *
     * @param  ch
     *         The channel to which bytes will be written
     *
     * @param  csName
     *         The name of the charset to be used
     *
     * @return  A new writer
     *
     * @throws  UnsupportedCharsetException
     *          If no support for the named charset is available
     *          in this instance of the Java virtual machine
     */
    public static Writer newWriter(WritableByteChannel ch, String csName) {
        return java.nio.channels.Channels.newWriter(ch, csName);
    }
}
