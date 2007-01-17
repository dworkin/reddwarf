/**
 * The I/O package provides the high level abstraction for using I/O services.
 * Clients should use these interfaces, regardless of the implementation that
 * backs them.
 * <p>
 * To use this framework, typically a client would obtain an
 * {@link com.sun.sgs.io.IOConnector} or {@link com.sun.sgs.io.IOAcceptor}
 * implementation through an {@link com.sun.sgs.io.Endpoint}.
 * <p>
 * Note that {@code byte} arrays are used throughout.  At some 
 * point they may be replaced by a custom {@code ByteBuffer} implementation.
 */
package com.sun.sgs.io;