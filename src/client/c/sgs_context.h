/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations relating to session contexts.
 */

#ifndef SGS_CONTEXT_H
#define SGS_CONTEXT_H 1

/*
 * Opaque pointer (declare before any #includes)
 */
typedef struct sgs_context_impl *sgs_context;

/*
 * INCLUDES
 */
#include "sgs_connection.h"
#include "sgs_id.h"
#include "sgs_session.h"

/*
 * FUNCTION DECLARATIONS
 */

// TODO - comments

sgs_context sgs_ctx_create(const char *hostname, const int port,
    void (*reg_fd)(sgs_connection, int[], size_t, short),
    void (*unreg_fd)(sgs_connection, int[], size_t, short));

void sgs_ctx_destroy(sgs_context ctx);

void sgs_ctx_set_channel_joined_cb(sgs_context ctx,
    void (*callback)(sgs_connection, const sgs_id*, const uint8_t*, size_t));

void sgs_ctx_set_channel_left_cb(sgs_context ctx,
    void (*callback)(sgs_connection, const sgs_id*));

void sgs_ctx_set_channel_recv_msg_cb(sgs_context ctx,
    void (*callback)(sgs_connection, const sgs_id*, const sgs_id*, const uint8_t*, size_t));

void sgs_ctx_set_disconnected_cb(sgs_context ctx,
    void (*callback)(sgs_connection));

void sgs_ctx_set_logged_in_cb(sgs_context ctx,
    void (*callback)(sgs_connection, sgs_session));

void sgs_ctx_set_login_failed_cb(sgs_context ctx,
    void (*callback)(sgs_connection, const uint8_t*, size_t));

void sgs_ctx_set_reconnected_cb(sgs_context ctx,
    void (*callback)(sgs_connection));

void sgs_ctx_set_recv_msg_cb(sgs_context ctx,
    void (*callback)(sgs_connection, const uint8_t*, size_t));

void sgs_ctx_unset_all_cbs(sgs_context ctx);

#endif  /** #ifndef SGS_CONTEXT_H */
