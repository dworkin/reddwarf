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

#ifndef SGS_ERROR_CODES_H
#define SGS_ERROR_CODES_H 1

#ifdef __cplusplus
extern "C" {
#endif

/** Custom errors: */

/** The code has reached a state that it shouldn't (probable bug). */
#define SGS_ERR_ILLEGAL_STATE  180

/** A message was received with an invalid version-id field. */
#define SGS_ERR_BAD_MSG_VERSION  181

/** A message was received with an unrecognized service-id field. */
#define SGS_ERR_BAD_MSG_SERVICE  182

/** A message was received with an unrecognized opcode field. */
#define SGS_ERR_BAD_MSG_OPCODE  183 

/** A size_t argument was too big to be represented in a network message. */
#define SGS_ERR_SIZE_ARG_TOO_LARGE 184

/** A function failed that sets herrno instead of errno. */
#define SGS_ERR_CHECK_HERRNO  185

/** The server sent a mesage referring to an unknown channel. */
#define SGS_ERR_UNKNOWN_CHANNEL 186

#ifdef __cplusplus
}
#endif

#endif /* !SGS_ERROR_CODES_H */
