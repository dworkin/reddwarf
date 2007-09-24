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
#include "sgs/message.h"

struct sgs_channel_impl {
    /* The underlying server session. */
    sgs_session_impl* session;
  
    /* Server-assigned unique ID for this channel. */
    sgs_id* id;
    
    /* Name of this channel. */
    wchar_t* name;
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
sgs_id* sgs_channel_impl_get_id(sgs_channel_impl* channel);

/*
 * function: sgs_channel_impl_create()
 *
 * Creates a new sgs_channel from the specified session with the specified name
 * and id.  Returns null on failure.
 */
sgs_channel_impl* sgs_channel_impl_create(sgs_session_impl* session,
    const sgs_id* id, const char* namebytes, size_t namelen);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CHANNEL_IMPL_H */
