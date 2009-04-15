/*
 * Copyright (c) 2007 - 2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef SGS_SOCKET_H
#define SGS_SOCKET_H 1

#ifdef __cplusplus
extern "C" {
#endif

/*
 * This file provides types and functions that unify handling of Berkeley-style
 * and Windows-style sockets.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "sgs/config.h"

/*
 * The type sgs_socket_t provides an abstraction around a socket descriptor.
 */
#ifdef WIN32
typedef SOCKET sgs_socket_t;
#else /* !WIN32 */
typedef int sgs_socket_t;
#endif /* !WIN32 */

/*
 * SGS_SOCKET_INVALID represents an invalid or uninitialized sgs_socket_t variable.
 */
#ifdef WIN32
#define SGS_SOCKET_INVALID INVALID_SOCKET
#else /* !WIN32 */
#define SGS_SOCKET_INVALID -1
#endif /* !WIN32 */

#ifdef WIN32
#define POLLIN		0x01
#define POLLOUT		0x02
#define POLLERR		0x04
#endif /* WIN32 */

/*
 * function: sgs_socket_create()
 *
 * Creates a new socket of the specified address family and type.  The socket
 * is created in blocking mode. Returns SGS_SOCKET_INVALID on error, while also 
 * setting errno to the specific error code.
 *
 * This is a wrapper around the standard sockets function socket.
 */
sgs_socket_t sgs_socket_create(int af, int type, int protocol);

/*
 * function: sgs_socket_destroy()
 *
 * Performs any necessary work to close an sgs_socket_t.
 *
 * This is a wrapper around the Windows Sockets function closesocket and the
 * Berkeley Sockets function close.
 */
int sgs_socket_destroy(sgs_socket_t socket_fd);

/*
 * function: sgs_socket_set_nonblocking()
 *
 * Changes an sgs_socket_t from blocking to non-blocking mode.
 *
 * This is a wrapper around the Windows Sockets function ioctlsocket and the
 * Berkeley Sockets function fcntl.
 */
int sgs_socket_set_nonblocking(sgs_socket_t socket_fd);

/*
 * function: sgs_socket_connect()
 *
 * Starts a connection to the server specified by name.
 *
 * This is a wrapper around the standards sockets function connect.
 */
int sgs_socket_connect(sgs_socket_t fd, const struct sockaddr* name, socklen_t name_len);

/*
 * function: sgs_socket_getsockopt()
 *
 * Retrieves a socket option.
 *
 * This is a wrapper around the standards socket function getsockopt.
 */
int sgs_socket_getsockopt(sgs_socket_t socket_fd, int level, int option_name, void* option_value, socklen_t* option_len);

/*
 * function: sgs_socket_read()
 *
 * Reads data from a socket.
 *
 * This is a wrapper around the Windows Sockets function recv and the
 * Berkeley Sockets function read.
 */
ssize_t sgs_socket_read(sgs_socket_t socket_fd, void* buffer, ssize_t nbyte);

/*
 * function: sgs_socket_write()
 *
 * Writes data to a socket.
 *
 * This is a wrapper around the Windows Sockets function send and the
 * Berkeley Sockets function write.
 */
ssize_t sgs_socket_write(sgs_socket_t socket_fd, void* buffer, ssize_t nbyte);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_SOCKET_H */
