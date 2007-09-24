#ifndef SGS_CONTEXT_IMPL_H
#define SGS_CONTEXT_IMPL_H 1

#include "sgs/config.h"
#include "sgs/context.h"
#include "sgs/connection.h"
#include "sgs/id.h"
#include "sgs/session.h"
#include "sgs/channel.h"

typedef struct sgs_context_impl sgs_context_impl;

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

#endif /* !SGS_CONTEXT_IMPL_H */
