/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides declarations for an implementation of functions relating
 * to byte-buffers.
 */

#ifndef SGS_BUFFER_IMPL_H
#define SGS_BUFFER_IMPL_H  1

/*
 * sgs_buffer_impl typedef(declare before any #includes)
 */
typedef struct sgs_buffer_impl sgs_buffer_impl;

/*
 * INCLUDES
 */
#include <stdint.h>
#include <stdlib.h>

/*
 * STRUCTS
 */
struct sgs_buffer_impl {
    /* Total amount of memory allocated to the "buf" pointer. */
    size_t capacity;
  
    /* Current position of the start of the data in the buffer. */
    size_t position;
  
    /* Number of bytes currently stored in the buffer. */
    size_t size;
  
    /* Array of the actual data. */
    uint8_t *buf;
    
    /* Flag used to indicate if a call to read() reached end-of-file */
    int eof;
};

#endif  /** #ifndef SGS_BUFFER_IMPL_H */
