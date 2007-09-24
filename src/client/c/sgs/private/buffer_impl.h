/*
 * This file provides an implementation of a circular byte-buffer.
 */

#ifndef SGS_BUFFER_IMPL_H
#define SGS_BUFFER_IMPL_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"
#include "sgs/buffer.h"

typedef struct sgs_buffer_impl sgs_buffer_impl;

struct sgs_buffer_impl {
    /* Total amount of memory allocated to the "buf" pointer. */
    size_t capacity;
  
    /* Current position of the start of the data in the buffer. */
    size_t position;
  
    /* Number of bytes currently stored in the buffer. */
    size_t size;
  
    /* Array of the actual data. */
    uint8_t* buf;
};

extern size_t readable_len(const sgs_buffer_impl* buffer);
extern size_t tailpos (const sgs_buffer_impl* buffer);
extern size_t writable_len(const sgs_buffer_impl* buffer);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_BUFFER_IMPL_H */
