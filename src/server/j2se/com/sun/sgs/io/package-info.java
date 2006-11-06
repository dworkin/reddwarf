/**
 * The I/O package provides the high level abstraction for using I/O services.
 * Clients should use these interfaces, regardless of the implementation that
 * backs them.
 * <p>
 * To use this framework, typically a client would obtain a
 * <code>ConnectionManager</code> implementation.
 * <p>
 * The specific implementation of a <code>Connector</code> is dictated
 * by the type of I/O and the desired features of the underlying framework
 * implementation (for example thread pools, etc.).
 *
 *
 */
package com.sun.sgs.io;