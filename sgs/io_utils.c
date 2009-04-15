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
 * This file provides an implementation of a circular byte-buffer.
 */

#include "sgs/config.h"
#include "sgs/private/io_utils.h"
#include "sgs/private/buffer_impl.h"

/*
 * sgs_impl_read_from_fd()
 */
ssize_t sgs_impl_read_from_fd(sgs_buffer_impl *buffer, sgs_socket_t fd) {
    ssize_t result, total = 0;
    ssize_t writable = writable_len(buffer);
    
    while (writable > 0) {
        result = sgs_socket_read(fd, buffer->buf + tailpos(buffer), writable);
        if (result == -1) return -1;  /* error */
        
        if (result == 0) {   /* EOF */
            return total;
        }
        
        total += result;
        buffer->size += result;
        if (result != writable) return total;  /* partial read */
        writable = writable_len(buffer);
    }
  
    return total;  /* buffer is full */
}

/*
 * sgs_impl_write_to_fd()
 */
ssize_t sgs_impl_write_to_fd(sgs_buffer_impl *buffer, sgs_socket_t fd) {
    ssize_t result, total = 0;
    ssize_t readable = readable_len(buffer);
  
    while (readable > 0) {
        result = sgs_socket_write(fd, buffer->buf + buffer->position, readable);
        if (result == -1) return -1;  /* error */
        total += result;
        buffer->position = (buffer->position + result) % buffer->capacity;
        buffer->size -= result;
        if (result != readable) return total;  /* partial write */
        readable = readable_len(buffer);
    }
  
    return total;  /* buffer is empty */
}
