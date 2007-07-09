/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for the sgs_channel_impl struct, which
 * implements the sgs_channel interface.
 */

#ifndef SGS_CHANNEL_IMPL_H
#define SGS_CHANNEL_IMPL_H 1

/*
 * sgs_channel_impl typedef (declare before any #includes)
 */
typedef struct sgs_channel_impl sgs_channel_impl;

/*
 * INCLUDES
 */
#include <stdint.h>
#include "sgs_session_impl.h"
#include "sgs_id.h"
#include "sgs_message.h"

/*
 * STRUCTS
 */
struct sgs_channel_impl {
    /** The underlying server session. */
    sgs_session_impl *session;
  
    /** Server-assigned unique ID for this channel. */
    sgs_id id;
    
    /** Name of this channel. */
    char *name;
};

/*
 * function: sgs_channel_impl_free()
 *
 * Performs any necessary memory deallocations to dispose of an sgs_channel.
 */
void sgs_channel_impl_free(sgs_channel_impl *channel);

/*
 * function: sgs_channel_impl_get_id()
 *
 * Returns a pointer to this channel's ID.
 */
sgs_id *sgs_channel_impl_get_id(sgs_channel_impl *channel);

/*
 * function: sgs_channel_impl_new()
 *
 * Creates a new sgs_channel from the specified session with the specified name
 * and id.  Returns null on failure.
 */
sgs_channel_impl *sgs_channel_impl_new(sgs_session_impl *session,
    const sgs_id id, const char *name, size_t namelen);

#endif  /** #ifndef SGS_CHANNEL_IMPL_H */
