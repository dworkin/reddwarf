/*
 * Copyright (c) 2007, 2008 Sun Microsystems, Inc.
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

#include "config.h"
#include "buffer.h"
#include "private/buffer_impl.h"

/*
 * sgs_buffer_capacity()
 */
size_t sgs_buffer_capacity(const sgs_buffer_impl* buffer) {
    return buffer->capacity;
}

size_t sgs_buffer_position(const sgs_buffer_impl* buffer) {
    return buffer->position;
}

#ifndef NDEBUG
# include <stdio.h>
void sgs_buffer_dump(const sgs_buffer_impl* buf) {
    size_t i;
    for (i = 0; i < buf->size; ++i) {
        printf("%2.2x ", buf->buf[i]);
    }
}
#else /* NDEBUG */
void sgs_buffer_dump(const sgs_buffer_impl* buf) { }
#endif /* NDEBUG */

/*
 * sgs_buffer_clear()
 */
void sgs_buffer_clear(sgs_buffer_impl* buffer) {
    buffer->position = 0;  /** not necessary, but convenient */
    buffer->size = 0;
}

/*
 * sgs_buffer_destroy()
 */
void sgs_buffer_destroy(sgs_buffer_impl* buffer) {
    free(buffer->buf);
    free(buffer);
}

/*
 * sgs_buffer_create()
 */
sgs_buffer_impl* sgs_buffer_create(size_t capacity) {
    sgs_buffer_impl* buffer;

    buffer = (sgs_buffer_impl*)malloc(sizeof(struct sgs_buffer_impl));
    if (buffer == NULL) return NULL;

    buffer->buf = (uint8_t*)malloc(capacity);

    if (buffer->buf == NULL) {
        /** "roll back" allocation of memory for buffer struct itself. */
        free(buffer);
        return NULL;
    }

    buffer->capacity = capacity;
    buffer->position = 0;
    buffer->size = 0;

    return buffer;
}

/*
 * sgs_buffer_peek()
 */
int sgs_buffer_peek(const sgs_buffer_impl* buffer, uint8_t* data, size_t len) {
    size_t readable = readable_len(buffer);

    if (len > buffer->size) return -1;

    if (readable >= len) {
        memcpy(data, buffer->buf + buffer->position, len);
    } else {
        memcpy(data, buffer->buf + buffer->position, readable);
        memcpy(data + readable, buffer->buf, len - readable);
    }

    return 0;
}

/*
 * sgs_buffer_read()
 */
int sgs_buffer_read(sgs_buffer_impl* buffer, uint8_t* data, size_t len) {
    if (sgs_buffer_peek(buffer, data, len) == -1) return -1;

    buffer->position = (buffer->position + len) % buffer->capacity;
    buffer->size -= len;
    return 0;
}

/*
 * sgs_buffer_remaining()
 */
size_t sgs_buffer_remaining(const sgs_buffer_impl* buffer) {
    return buffer->capacity - buffer->size;
}

/*
 * sgs_buffer_size()
 */
size_t sgs_buffer_size(const sgs_buffer_impl* buffer) {
    return buffer->size;
}

/*
 * sgs_buffer_write()
 */
int sgs_buffer_write(sgs_buffer_impl* buffer, const uint8_t* data, size_t len) {
    size_t writable = writable_len(buffer);

    if (len > sgs_buffer_remaining(buffer)) {
        errno = EINVAL;
        return -1;
    }

    if (writable >= len) {
        memcpy(buffer->buf + tailpos(buffer), data, len);
    } else {
        memcpy(buffer->buf + tailpos(buffer), data, writable);
        memcpy(buffer->buf, data + writable, len - writable);
    }

    buffer->size += len;
    return 0;
}

/*
 * PRIVATE FUNCTION IMPLEMENTATIONS
 */

/*
 * readable_len()
 */
size_t readable_len(const sgs_buffer_impl* buffer) {
    if (buffer->size == 0) return 0;

    if (tailpos(buffer) > buffer->position) {
        /*
         * The stored data has not wrapped yet, so we can read until we read the
         * tail.
         */
        return buffer->size;
    } else {
        /**
         * The stored data HAS wrapped, we can we read until we reach the end of
         * the memory block.
         */
        return buffer->capacity - buffer->position;
    }
}

/*
 * tailpos()
 */
size_t tailpos(const sgs_buffer_impl* buffer) {
    return (buffer->position + buffer->size) % buffer->capacity;
}

/*
 * writable_len()
 */
size_t writable_len(const sgs_buffer_impl* buffer) {
    size_t mytailpos;

	if (buffer->size == buffer->capacity) return 0;

	mytailpos = tailpos(buffer);

    if (mytailpos >= buffer->position) {
        /*
         * The stored data has not wrapped yet, so we can write until we reach
         * the end of the memory block.
         */
        return buffer->capacity - mytailpos;
    } else {
        /**
         * The stored data HAS wrapped, so we can write until we reach the
         * head.
        */
        return buffer->position - mytailpos;
    }
}
