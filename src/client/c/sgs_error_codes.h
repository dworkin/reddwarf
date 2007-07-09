/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

#ifndef SGS_ERROR_CODES_H
#define SGS_ERROR_CODES_H 1

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

/** sgs_connection_do_io() was called with an unregistered FD. */
#define SGS_ERR_BAD_FD 186

/** The server sent a mesage referring to an unknown channel. */
#define SGS_ERR_UNKNOWN_CHANNEL 187

#endif  /** #ifndef SGS_ERROR_CODES_H */
