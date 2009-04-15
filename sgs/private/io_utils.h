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

#ifndef SGS_IO_UTILS_H
#define SGS_IO_UTILS_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"
#include "sgs/buffer.h"
#include "sgs/socket.h"

/*
 * function: sgs_impl_read_from_fd()
 *
 * Copies data from the specified file descriptor into the buffer.  Copying
 * stops if (a) the buffer runs out of room, or (b) a call to read() on the file
 * descriptor returns any value other than the requested length.  If copying
 * stops because the buffer ran out of room or because a call to read() returned
 * a valued smaller than the requested read size, then the total number of bytes
 * read into the buffer is returned.  Otherwise, if read ever returns -1,
 * indicating an error, then -1 is returned.  Note that if this method returns 0
 * it may indicate that the buffer is full, or that read() returned 0,
 * indicating that end-of-file was read.
 */
ssize_t sgs_impl_read_from_fd(sgs_buffer* buffer, sgs_socket_t fd);

/*
 * function: sgs_impl_write_to_fd()
 *
 * Copies len bytes of data out of the buffer and writes them to the specified
 * file descriptor.  Writing stops if (a) the buffer runs out of data, or (b) a
 * call to write() on the file descriptor returns any value other than the
 * requested length.  Returns -1 if an error occurs; otherwise returns the total
 * number of bytes written to the file descriptor.
 */
ssize_t sgs_impl_write_to_fd(sgs_buffer* buffer, sgs_socket_t fd);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_IO_UTILS_H */
