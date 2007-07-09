/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for an implementation of session contexts.
 */

#ifndef SGS_CONTEXT_IMPL_H
#define SGS_CONTEXT_IMPL_H 1

/*
 * sgs_context_impl typedef (declare before any #includes)
 */
typedef struct sgs_context_impl sgs_context_impl;

/*
 * INCLUDES
 */
#include "sgs_channel.h"
#include "sgs_connection.h"
#include "sgs_id.h"
#include "sgs_session.h"

/*
 * STRUCTS
 */
struct sgs_context_impl {
    /** Hostname and port number (of server) to connect to: */
    char hostname[100];
    int port;
  
    /** function pointers to callbacks: */
    void (*reg_fd_cb)(sgs_connection*, int, short);
    void (*unreg_fd_cb)(sgs_connection*, int, short);
  
    void (*channel_joined_cb)(sgs_connection*, sgs_channel*);
    void (*channel_left_cb)(sgs_connection*, sgs_channel*);
    void (*channel_recv_msg_cb)(sgs_connection*, sgs_channel*, const sgs_id*,
        const uint8_t*, size_t);
    void (*disconnected_cb)(sgs_connection*);
    void (*logged_in_cb)(sgs_connection*, sgs_session*);
    void (*login_failed_cb)(sgs_connection*, const uint8_t*, size_t);
    void (*reconnected_cb)(sgs_connection*);
    void (*recv_message_cb)(sgs_connection*, const uint8_t*, size_t);
};

#endif  /** #ifndef SGS_CONTEXT_IMPL_H */
