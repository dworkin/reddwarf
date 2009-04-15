/*
 * Copyright (c) 2007 - 2009 Sun Microsystems, Inc.
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
 * This file provides an implementation of a circular byte-buffer. The buffer is
 *created with a call to sgs_buffer_create, which indicates the size of the buffer.
 *When data is written to the buffer, it can then be read by someone else. The current
 *starting point for reading is updated by reads, and the buffer keeps track of the
 *amount of unread data that is stored in the buffer. Attempts to write over data that
 *has not been read will result in an error. 
 */

#include "sgs/config.h"
#include "sgs/buffer.h"

#include "sgs/private/buffer_impl.h"

/*
 * sgs_buffer_capacity()
 *Returns the total capacity of the buffer. This does not take
 *into account the current contents of the buffer, so the capacity
 *may be greater than the amount of data that can be absorbed
 *at the moment.
 */
size_t sgs_buffer_capacity(const sgs_buffer_impl* buffer) {
    return buffer->capacity;
}

/*
 *Returns the current cursor position within the buffer.
 *This cursor states where any read will begin within the
 *buffer
 */
size_t sgs_buffer_position(const sgs_buffer_impl* buffer) {
    return buffer->position;
}

#ifndef NDEBUG
#include <stdio.h>

void sgs_buffer_dump(const sgs_buffer_impl* buf) {
    size_t i;
    for (i = 0; i < buf->size; ++i) {
        printf("%2.2x ", buf->buf[i]);
    }
}
#else /* NDEBUG */

void sgs_buffer_dump(const sgs_buffer_impl* buf) {
}
#endif /* NDEBUG */

/*
 * sgs_buffer_clear()
 *Resets the buffer so that the current contents, if any,
 *will be over-written. 
 */
void sgs_buffer_clear(sgs_buffer_impl* buffer) {
    buffer->position = 0; /** not necessary, but convenient */
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
 * Allocates a buffer with the specified capacity (in bytes).  NULL is
 * returned if allocation fails.
 */
sgs_buffer_impl* sgs_buffer_create(size_t capacity) {
    sgs_buffer_impl* buffer;

    buffer = (sgs_buffer_impl*) malloc(sizeof (struct sgs_buffer_impl));
    if (buffer == NULL) return NULL;

    buffer->buf = (uint8_t*) malloc(capacity);

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
 *Copy the contents of the data array passed in to the indicated buffer, up
 *to the specified length. The length of the data to be copied must be
 *smaller than the amount of space remaining in the buffer, or a value of -1
 *is returned. Otherwise, the function returns 0
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
 *Reads len bytes from a buffer, and returns them in the array data. It
 *is up to the caller to allocate the data array of at least len size. After
 *reading, the current buffer position and size are updated. Returns 0 if
 * the bytes are successfully copied, or -1 if there was an error.
 */
int sgs_buffer_read(sgs_buffer_impl* buffer, uint8_t* data, size_t len) {
    if (sgs_buffer_peek(buffer, data, len) == -1) return -1;

    buffer->position = (buffer->position + len) % buffer->capacity;
    buffer->size -= len;
    return 0;
}

/*
 * sgs_buffer_remaining()
 *Returns the amount of free space that remains available within a
 *buffer. Free space is defined as space that has had nothing written to
 *it, or which has had something written to it that has subsequently been
 *read
 */
size_t sgs_buffer_remaining(const sgs_buffer_impl* buffer) {
    return buffer->capacity - buffer->size;
}

/*
 * sgs_buffer_size()
 *Returns the amount of data stored in the buffer that has not yet
 *been used. This is space that should not be used in the buffer, since
 *it contains data that needs to be passed on to some recipient.
 */
size_t sgs_buffer_size(const sgs_buffer_impl* buffer) {
    return buffer->size;
}

/*
 * sgs_buffer_write()
 *Write data of size len from data to the indicated buffer. Once written,
 *the size of the buffer is increased by the amount of data written. If the
 *amount of data to be written is greater than the amount of free space in
 *the buffer, the routine returns -1 and sets errno; otherwise returns 0
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
 *Returns the amount of contiguous data that can be read from
 *the buffer, which can be used in memcpy to directly
 *copy from the buffer to some other data array.
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
 *Returns the last position of the data that is held in the array.
 *Note that if the data has wrapped, this may be less than the
 *current position of reading in the buffer.
 */
size_t tailpos(const sgs_buffer_impl* buffer) {
    return (buffer->position + buffer->size) % buffer->capacity;
}

/*
 * writable_len()
 *Returns the number of consecutive bytes that can be  written
 *into the buffer, which can be used by memcpy to transfer
 *data from some array directly into the buffer.
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
