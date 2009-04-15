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
#include "sgs/socket.h"

#ifdef WIN32
/*
 * function: MapWSAErrorToErrno()
 *
 * Certain Windows Socket error codes have standard errno equivalents that are used
 * else in the C Client's codebase.  This function performs that mapping.
 */
int MapWSAErrorToErrno(int wsaError)
{
	int i;
	static struct { int wsaError; int error; } gErrorMappings[] =
	{
		{ WSAEWOULDBLOCK, EINPROGRESS },
		{ WSAECONNREFUSED, ECONNREFUSED },
        { WSAETIMEDOUT, ETIMEDOUT },
	};
	for (i = 0; i < (sizeof(gErrorMappings) / sizeof(gErrorMappings[0])); ++i)
		if (gErrorMappings[i].wsaError == wsaError)
			return gErrorMappings[i].error;
	return wsaError;
}
#endif

#ifndef WIN32
#include <fcntl.h>
#include <unistd.h>
#endif

sgs_socket_t sgs_socket_create(int af, int type, int protocol)
{
    sgs_socket_t socket_fd = socket(af, type, protocol);
#ifdef WIN32
    if (INVALID_SOCKET == socket_fd)
        errno = MapWSAErrorToErrno(WSAGetLastError());
#endif /* WIN32 */
    return socket_fd;
}

int sgs_socket_destroy(sgs_socket_t socket_fd)
{
#ifdef WIN32
    int result = closesocket(socket_fd);
    if (SOCKET_ERROR == result)
        errno = MapWSAErrorToErrno(WSAGetLastError());
    return result;
#else /* !WIN32 */
    return close(socket_fd);
#endif /* !WIN32 */
}

int sgs_socket_set_nonblocking(sgs_socket_t socket_fd)
{
#ifdef WIN32
    u_long non_blocking_flag = 1;
    int result = ioctlsocket(socket_fd, FIONBIO, &non_blocking_flag);
    if (SOCKET_ERROR == result)
        errno = MapWSAErrorToErrno(WSAGetLastError());
    return result;
#else /* !WIN32 */
    int ioflags;
    if ((ioflags = fcntl(socket_fd, F_GETFL, 0)) == -1) return -1;
    return fcntl(socket_fd, F_SETFL, ioflags | O_NONBLOCK);
#endif /* !WIN32 */
}

int sgs_socket_connect(sgs_socket_t fd, const struct sockaddr* name, socklen_t name_len)
{
    int result = connect(fd, name, name_len);
#ifdef WIN32
    if (SOCKET_ERROR == result)
        errno = MapWSAErrorToErrno(WSAGetLastError());
#endif /* WIN32 */
    return result;
}

int sgs_socket_getsockopt(sgs_socket_t socket_fd, int level, int option_name, void* option_value, socklen_t* option_len)
{
    int result = getsockopt(socket_fd, level, option_name, option_value, option_len);
#ifdef WIN32
    if (SOCKET_ERROR == result)
        errno = MapWSAErrorToErrno(WSAGetLastError());
    else if (level == SOL_SOCKET && option_name == SO_ERROR)
        *(int*)option_value = MapWSAErrorToErrno(*(int*)option_value);
#endif /* WIN32 */
    return result;
}

ssize_t sgs_socket_read(sgs_socket_t socket_fd, void* buffer, ssize_t nbyte)
{
#ifdef WIN32
    int result = recv(socket_fd, buffer, (int)nbyte, 0);
    if (SOCKET_ERROR == result)
        errno = MapWSAErrorToErrno(WSAGetLastError());
    return result;
#else /* !WIN32 */
    return read(socket_fd, buffer, nbyte);
#endif /* !WIN32 */
}

ssize_t sgs_socket_write(sgs_socket_t socket_fd, void* buffer, ssize_t nbyte)
{
#ifdef WIN32
    int result = send(socket_fd, buffer, (int)nbyte, 0);
    if (SOCKET_ERROR == result)
        errno = MapWSAErrorToErrno(WSAGetLastError());
    return result;
#else /* !WIN32 */
    return write(socket_fd, buffer, nbyte);
#endif /* !WIN32 */
}
