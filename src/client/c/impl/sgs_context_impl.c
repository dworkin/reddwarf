/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides an implementation of connection contexts.  Implements
 * functions declared in sgs_context.h.
 */

/*
 * INCLUDES
 */
#include <errno.h>
#include <string.h>
#include "sgs_context_impl.h"
#include "sgs_connection.h"
#include "sgs_id.h"

/*
 * EXTERNAL FUNCTION IMPLEMENTATIONS
 */

/*
 * sgs_ctx_new()
 */
sgs_context_impl *sgs_ctx_new(const char *hostname, const int port,
    void (*reg_fd)(sgs_connection*, int[], size_t, short),
    void (*unreg_fd)(sgs_connection*, int[], size_t, short))
{
  sgs_context_impl *ctx = NULL;
  
  ctx = (sgs_context_impl*)malloc(sizeof(struct sgs_context_impl));
  if (ctx == NULL) return NULL;
  
  if (strlen(hostname) + 1 > sizeof(ctx->hostname)) {
    /** Hostname is too big. */
    free(ctx);
    errno = ENOBUFS;
    return NULL;
  }
  
  strncpy(ctx->hostname, hostname, sizeof(ctx->hostname));
  ctx->port = port;
  
  ctx->reg_fd_cb = reg_fd;
  ctx->unreg_fd_cb = unreg_fd;
  
  sgs_ctx_unset_all_cbs(ctx);  /** initialize all callback functions to NULL */
  
  return ctx;
}

/*
 * sgs_ctx_free()
 */
void sgs_ctx_free(sgs_context_impl *ctx) {
  free(ctx);
}

/*
 * sgs_ctx_set_channel_joined_cb()
 */
void sgs_ctx_set_channel_joined_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*, const sgs_id*, const uint8_t*, size_t))
{
  ctx->channel_joined_cb = callback;
}

/*
 * sgs_ctx_set_channel_left_cb()
 */
void sgs_ctx_set_channel_left_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*, const sgs_id*))
{
  ctx->channel_left_cb = callback;
}

/* 
 * sgs_ctx_set_channel_recv_msg_cb()
 */
void sgs_ctx_set_channel_recv_msg_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*, const sgs_id*, const sgs_id*,
                     const uint8_t*, size_t))
{
  ctx->channel_recv_msg_cb = callback;
}

/*
 * sgs_ctx_set_disconnected_cb()
 */
void sgs_ctx_set_disconnected_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*))
{
  ctx->disconnected_cb = callback;
}

/*
 * sgs_ctx_set_logged_in_cb()
 */
void sgs_ctx_set_logged_in_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*, sgs_session*))
{
  ctx->logged_in_cb = callback;
}

/*
 * sgs_ctx_set_login_failed_cb()
 */
void sgs_ctx_set_login_failed_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*, const uint8_t*, size_t))
{
  ctx->login_failed_cb = callback;
}

/*
 * sgs_ctx_set_reconnected_cb()
 */
void sgs_ctx_set_reconnected_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*))
{
  ctx->reconnected_cb = callback;
}

/*
 * sgs_ctx_set_recv_msg_cb()
 */
void sgs_ctx_set_recv_msg_cb(sgs_context_impl *ctx,
    void (*callback)(sgs_connection*, const uint8_t*, size_t))
{
  ctx->recv_message_cb = callback;
}

/*
 * sgs_ctx_unset_all_cbs()
 */
void sgs_ctx_unset_all_cbs(sgs_context_impl *ctx)
{
  ctx->channel_joined_cb   = NULL;
  ctx->channel_left_cb     = NULL;
  ctx->channel_recv_msg_cb = NULL;
  ctx->disconnected_cb     = NULL;
  ctx->logged_in_cb        = NULL;
  ctx->login_failed_cb     = NULL;
  ctx->reconnected_cb      = NULL;
  ctx->recv_message_cb     = NULL;
}
