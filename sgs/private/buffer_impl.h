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

#ifndef SGS_BUFFER_IMPL_H
#define SGS_BUFFER_IMPL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"
#include "sgs/buffer.h"

typedef struct sgs_buffer_impl sgs_buffer_impl;

struct sgs_buffer_impl {
    /* Total amount of memory allocated to the "buf" pointer. */
    size_t capacity;
  
    /* Current position of the start of the data in the buffer. */
    size_t position;
  
    /* Number of bytes currently stored in the buffer. */
    size_t size;
  
    /* Array of the actual data. */
    uint8_t* buf;
};

extern size_t readable_len(const sgs_buffer_impl* buffer);
extern size_t tailpos (const sgs_buffer_impl* buffer);
extern size_t writable_len(const sgs_buffer_impl* buffer);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_BUFFER_IMPL_H */
