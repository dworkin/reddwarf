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
 * This file provides declarations relating to byte-buffers.
 */

#ifndef SGS_BUFFER_H
#define SGS_BUFFER_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

    typedef struct sgs_buffer_impl sgs_buffer;

    /*
     * function: sgs_buffer_capacity()
     *
     * Returns the total capacity of a buffer.
     */
    size_t sgs_buffer_capacity(const sgs_buffer* buffer);

    /*
     * function: sgs_buffer_position()
     *
     * Returns the curent position of a buffer.
     */
    size_t sgs_buffer_position(const sgs_buffer* buffer);

    /*
     * function: sgs_buffer_dump()
     *
     * Dumps a buffer to stdout (noop if NDEBUG defined).
     */
    void sgs_buffer_dump(const sgs_buffer* buffer);

    /*
     * function: sgs_buffer_clear()
     *
     * Clears all data from the buffer.  This doesnt' actually write anything to the
     * buffer's memory but merely resets the internal state so that the buffer
     * "forgets" about any data currently held.
     */
    void sgs_buffer_clear(sgs_buffer* buffer);

    /*
     * function: sgs_buffer_destroy()
     *
     * Safely deallocates a buffer.
     */
    void sgs_buffer_destroy(sgs_buffer* buffer);

    /*
     * function: sgs_buffer_create()
     *
     * Allocates a buffer with the specified capacity (in bytes).  NULL is
     * returned if allocation fails.
     */
    sgs_buffer* sgs_buffer_create(size_t capacity);

    /*
     * function: sgs_buffer_peek()
     *
     * Copies len bytes of data out of the buffer into the specified array, but does
     * not update the buffer's internal state (such that subsequent calls to
     * sgs_buffer_peek() or sgs_buffer_write() will reread the same bytes again).
     * Returns 0 on success or -1 if the buffer does not contain enough data to
     * satisfy the request.
     */
    int sgs_buffer_peek(const sgs_buffer* buffer, uint8_t* data, size_t len);


    /*
     * function: sgs_buffer_read()
     *
     * Copies len bytes of data out of the buffer into the specified array.  Returns
     * 0 on success or -1 if the buffer does not contain enough data to satisfy the
     * request.
     */
    int sgs_buffer_read(sgs_buffer* buffer, uint8_t* data, size_t len);

    /*
     * function: sgs_buffer_remaining()
     *
     * Returns the amount of space remaining in a buffer (capacity - size).  This is
     * NOT necessarily the number of bytes that can be safely written (contiguously)
     * to the tail of the buffer (since the buffer is circular).  For this function,
     * use sgs_buffer_writable_len().
     */
    size_t sgs_buffer_remaining(const sgs_buffer* buffer);

    /*
     * function: sgs_buffer_size()
     *
     * Returns the current size of the stored data.
     */
    size_t sgs_buffer_size(const sgs_buffer* buffer);

    /*
     * function: sgs_buffer_write()
     *
     * Copies data into the buffer as long as the length of the data is less than
     * the remaining capacity of the buffer, returning 0.  Otherwise, returns -1 and
     * errno is set to EINVAL.
     */
    int sgs_buffer_write(sgs_buffer* buffer, const uint8_t* data, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_BUFFER_H */
