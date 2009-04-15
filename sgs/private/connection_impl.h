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

#ifndef SGS_CONNECTION_IMPL_H
#define SGS_CONNECTION_IMPL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"
#include "sgs/connection.h"
#include "sgs/socket.h"

    typedef struct sgs_connection_impl sgs_connection_impl;

    typedef enum {
        SGS_CONNECTION_IMPL_DISCONNECTED,
        SGS_CONNECTION_IMPL_CONNECTING,
        SGS_CONNECTION_IMPL_CONNECTED,
    } sgs_connection_state;

#include "sgs/private/context_impl.h"
#include "sgs/private/session_impl.h"
#include "sgs/buffer.h"

    struct sgs_connection_impl {
        /** File descriptor for the network socket to the server. */
        sgs_socket_t socket_fd;

        /** The current state of the connection. */
        sgs_connection_state state;

        /** The login context (contains all callback functions). */
        sgs_context_impl* ctx;

        /** The session with the server (once connected). */
        sgs_session_impl* session;

        /** Reusable I/O buffers for reading/writing from/to the network
         * connection. */
        sgs_buffer* inbuf;
        sgs_buffer* outbuf;

        /** Whether we expect the server to close the socket: 1 = yes, 0 = no */
        int expecting_disconnect;
        /** Whether we expect a disconnect from part of a redirect: 1 = yes, 0 = no*/
        int in_redirect;
        /* flags to determine if the input and output buffers are currently enabled;
            a value of 0 indicates that the buffers are not enabled; 1 indicates that the
            buffers are enabled
         */
        int input_enabled;
        int output_enabled;
    };


    /*
     * function: sgs_connection_impl_disconnect()
     *
     * Closes the network connection of a connection.
     */
    void sgs_connection_impl_disconnect(sgs_connection_impl *connection);

    /*
     * function: sgs_connection_impl_io_write()
     *
     * Writes buflen bytes from the buf array to the connection's underlying socket.
     */
    int sgs_connection_impl_io_write(sgs_connection_impl *connection, uint8_t *buf,
            size_t buflen);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_CONNECTION_IMPL_H */
