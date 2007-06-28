/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides declarations for a circular byte-buffer.
 */

#ifndef SGS_BUFFER_H
#define SGS_BUFFER_H  1

/*
 * Opaque pointer (declare before any #includes)
 */
typedef struct sgs_buffer *sgs_buffer;

/*
 * INCLUDES
 */
#include <stdint.h>
#include <stdlib.h>

/*
 * STRUCTS
 */
struct sgs_buffer {
  /* Total amount of memory allocated to the "buf" pointer. */
  size_t capacity;
  
  /* Current position of the start of the data in the buffer. */
  size_t position;
  
  /* Number of bytes currently stored in the buffer. */
  size_t size;
  
  /* Array of the actual data. */
  uint8_t *buf;
  
  /* Used in mark() and reset() methods.*/
  size_t marked_position, marked_size;
};

/*
 * FUNCTION DECLARATIONS
 */

/*
 * function: sgs_buffer_can_read()
 *
 * Returns whether the specified number of bytes can be safely read from the
 * head of the buffer.  May rearrange the internals of the buffer, invalidating
 * any previously held head or tail pointers.
 */
int sgs_buffer_can_read(sgs_buffer buffer, size_t len);

/*
 * function: sgs_buffer_can_write()
 *
 * Returns whether the specified number of bytes can be safely written to the
 * tail of the buffer.  May rearrange the internals of the buffer, invalidating
 * any previously held head or tail pointers.
 */
int sgs_buffer_can_write(sgs_buffer buffer, size_t len);

/*
 * function: sgs_buffer_capacity()
 *
 * Returns the total capacity of a buffer.
 */
size_t sgs_buffer_capacity(sgs_buffer buffer);

/*
 * function: sgs_buffer_create()
 *
 * Allocates a buffer with at least the specified capacity (in bytes).  NULL is
 * returned if allocation fails.
 */
sgs_buffer sgs_buffer_create(size_t capacity);

/*
 * function: sgs_buffer_destroy()
 *
 * Safely deallocates a buffer.
 */
void sgs_buffer_destroy(sgs_buffer buffer);

/*
 * function: sgs_buffer_empty()
 *
 * Clears all data from the buffer.  This doesnt' actually write anything to the
 * buffer's memory but merely resets the internal state so that the buffer
 * "forgets" about any data currently held.
 */
void sgs_buffer_empty(sgs_buffer buffer);

/*
 * function: sgs_buffer_head()
 *
 * Returns a pointer to the first byte of data in the buffer.
 */
uint8_t *sgs_buffer_head(sgs_buffer buffer);

/*
 * function: sgs_buffer_mark()
 *
 * Marks the current head and tail positions.
 */
void sgs_buffer_mark(sgs_buffer buffer);

/*
 * function: sgs_buffer_read_update()
 *
 * Informs the buffer that len bytes have been read from the head of the buffer.
 * If this update would result in an underflow of the buffer size (meaning the
 * user read more bytes than were actually available), -1 is returned.
 * Otherwise, 0 is returned.
 */
int sgs_buffer_read_update(sgs_buffer buffer, size_t len);

/*
 * function: sgs_buffer_reset()
 *
 * Resets the head and tail positions to their positions when sgs_buffer_mark()
 * was last called.  If sgs_buffer_mark() has never been called, the result is
 * undefined.
 */
void sgs_buffer_reset(sgs_buffer buffer);

/*
 * function: sgs_buffer_remaining_capacity()
 *
 * Returns the amount of space remaining in a buffer (capacity - size).  This is
 * NOT necessarily the number of bytes that can be safely written (contiguously)
 * to the tail of the buffer (since the buffer is circular).  For this function,
 * use sgs_buffer_writable_len().
 */
size_t sgs_buffer_remaining_capacity(sgs_buffer buffer);

/*
 * function: sgs_buffer_size()
 *
 * Returns the current size of the stored data.
 */
size_t sgs_buffer_size(sgs_buffer buffer);

/*
 * function: sgs_buffer_tail()
 *
 * Returns a pointer to the first free element of the buffer (i.e. where new
 * data should be written to add it to the buffer).
 */
uint8_t *sgs_buffer_tail(sgs_buffer buffer);

/*
 * function: sgs_buffer_write_update()
 *
 * Informs the buffer that len bytes have been written to the tail of the
 * buffer.  If this update would result in an overflow of the buffer (meaning
 * the user wrote more bytes than there was room for), -1 is returned.
 * Otherwise, 0 is returned.
 */
int sgs_buffer_write_update(sgs_buffer buffer, size_t len);

#endif  /** #ifndef SGS_BUFFER_H */
