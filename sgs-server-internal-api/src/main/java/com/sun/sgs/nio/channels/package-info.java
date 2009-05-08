/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

/**
 Defines asynchronous channels that are connected to a stream-oriented
 connecting or listening socket, or a datagram-oriented socket. <p>

 <a name="async"></a>
 <blockquote><table cellspacing=1 cellpadding=0
 summary="Lists asynchronous channels and their descriptions">
 <tr><th><p align="left">Asynchronous I/O</p></th>
     <th><p align="left">Description</p></th></tr>
 <tr><td valign=top>{@link com.sun.sgs.nio.channels.AsynchronousChannel}</td>
 <td>A channel capable of asynchronous operations</td></tr>
 <tr><td valign=top>{@code AsynchronousFileChannel}</td>
 <td>[[NOT IMPLEMENTED]] An asynchronous channel for reading,
 writing, and manipulating a file</td></tr>

 <tr><td valign=top>
     {@link com.sun.sgs.nio.channels.AsynchronousSocketChannel}</td>
 <td>An asynchronous channel to a stream-oriented connecting socket</td></tr>
 <tr><td valign=top>
     {@link com.sun.sgs.nio.channels.AsynchronousServerSocketChannel}</td>
 <td>An asynchronous channel to a stream-oriented listening socket</td></tr>  
 <tr><td valign=top>
     {@link com.sun.sgs.nio.channels.AsynchronousDatagramChannel}</td>
 <td>An asynchronous channel to a datagram-oriented socket</td></tr>

 <tr><td valign=top>{@link com.sun.sgs.nio.channels.CompletionHandler}</td>
 <td>A handler for consuming the result of an asynchronous operation</td>
 </tr>   
 <tr><td valign=top>{@link com.sun.sgs.nio.channels.IoFuture}</td>
 <td>A Future representing the result of an asynchronous I/O operation</td>
 </tr> 
 <tr><td valign=top>
     {@link com.sun.sgs.nio.channels.AsynchronousChannelGroup}</td>
 <td>A grouping of asynchronous channels for the purpose of resource
 sharing</td></tr> 
 </table></blockquote>

 <p>
 Asynchronous I/O is provided by asynchronous channels, I/O futures, and 
 completion handlers. 
 <p>
 {@link com.sun.sgs.nio.channels.AsynchronousChannel Asynchronous channels} are
 a special type of channel capable of asynchronous I/O operations. Asynchronous
 channels are non-blocking and define methods to initiate asynchronous 
 operations, returning an {@link com.sun.sgs.nio.channels.IoFuture} representing
 the pending result of each operation. The {@code IoFuture} can be used to poll
 or wait for the result of the operation. Asynchronous I/O operations can also 
 specify a {@link com.sun.sgs.nio.channels.CompletionHandler} to invoke when 
 the operation completes. A completion handler is user provided code that is
 executed to consume the result of I/O operation.
 <p>
 This package defines asynchronous-channel classes that are connected to 
 a stream-oriented connecting or listening socket, or a datagram-oriented
 socket.  [[NOT IMPLEMENTED: It also defines the
 {@code AsynchronousFileChannel} class
 for asynchronous reading, writing, and manipulating a file. As with the
 {@code FileChannel} it supports operations to truncate the file
 to a specific size, force updates to the file to be written to the storage 
 device, or acquire locks on the whole file or on a specific region of the 
 file. Unlike the {@code FileChannel} it does not define methods for mapping a 
 region of the file directly into memory. Where memory mapped I/O is required,
 then a {@code FileChannel} can be used.]]
 <p>
 Asynchronous channels are bound to an asynchronous channel group for the 
 purpose of resource sharing. A group has an associated
 {@link java.util.concurrent.ExecutorService ExecutorService} to which tasks
 are submitted to handle I/O events and dispatch to completion handlers that
 consume the result of  asynchronous operations performed on channels in the 
 group. The group can optionally be specified when creating the channel or the
 channel can be bound to a <em>default group</em>. Sophisticated users may 
 wish to create their own asynchronous channel groups or configure the
 {@code ExecutorService} that will be used for the default group.
 <p>
 As with selectors, the implementation of asynchronous channels can be 
 replaced by "plugging in" an alternative definition or instance of the
 {@link com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider
 AsynchronousChannelProvider}
 class defined in the {@link java.nio.channels.spi} package.
 It is not expected that many developers will actually make use of this
 facility; it is provided primarily so that sophisticated users can take
 advantage of operating-system-specific asynchronous I/O mechanisms when very
 high performance is required.
 <hr width="80%">
 <p>
 Unless otherwise noted, passing a {@code null} argument to a constructor
 or method in any class or interface in this package will cause a
 {@link java.lang.NullPointerException} to be thrown.
 */
package com.sun.sgs.nio.channels;

