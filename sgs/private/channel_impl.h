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

#ifndef SGS_CHANNEL_IMPL_H
#define SGS_CHANNEL_IMPL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct sgs_channel_impl sgs_channel_impl;

#include "sgs/private/session_impl.h"
#include "sgs/error_codes.h"
#include "sgs/id.h"
#include "sgs/private/message.h"

struct sgs_channel_impl {
    /* The underlying server session. */
    sgs_session_impl* session;
  
    /* Server-assigned unique ID for this channel. */
    sgs_id* id;
    
    /* Name of this channel. */
    char* name;
};

/*
 * function: sgs_channel_impl_destroy()
 *
 * Performs any necessary memory deallocations to dispose of an sgs_channel.
 */
void sgs_channel_impl_destroy(sgs_channel_impl* channel);

/*
 * function: sgs_channel_impl_get_id()
 *
 * Returns a pointer to this channel's ID.
 */
const sgs_id* sgs_channel_impl_get_id(sgs_channel_impl* channel);

/*
 * function: sgs_channel_impl_create()
 *
 * Creates a new sgs_channel from the specified session with the specified name
 * and id.  Returns null on failure.
 */
sgs_channel_impl* sgs_channel_impl_create(sgs_session_impl* session,
    sgs_id* id, const char* namebytes, size_t namelen);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CHANNEL_IMPL_H */
