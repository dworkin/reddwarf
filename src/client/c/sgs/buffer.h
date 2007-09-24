/*
 * This file provides declarations relating to byte-buffers.
 */

#ifndef SGS_BUFFER_H
#define SGS_BUFFER_H 1

#ifdef __cplusplus
extern "C" {
#endif

#include "sgs/config.h"

typedef struct sgs_buffer_impl sgs_buffer;

/*
 * function: sgs_buffer_capacity()
 *
 * Returns the total capacity of a buffer.
 */
size_t sgs_buffer_capacity(const sgs_buffer* buffer);

// XXX remove?
size_t sgs_buffer_position(const sgs_buffer* buffer);

#ifndef NDEBUG
void sgs_buffer_dump(const sgs_buffer* buffer);
#endif /* !NDEBUG */

/*
 * function: sgs_buffer_clear()
 *
 * Clears all data from the buffer.  This doesnt' actually write anything to the
 * buffer's memory but merely resets the internal state so that the buffer
 * "forgets" about any data currently held.
 */
void sgs_buffer_clear(sgs_buffer* buffer);

/*
 * function: sgs_buffer_destroy()
 *
 * Safely deallocates a buffer.
 */
void sgs_buffer_destroy(sgs_buffer* buffer);

/*
 * function: sgs_buffer_create()
 *
 * Allocates a buffer with the specified capacity (in bytes).  NULL is
 * returned if allocation fails.
 */
sgs_buffer* sgs_buffer_create(size_t capacity);

/*
 * function: sgs_buffer_peek()
 *
 * Copies len bytes of data out of the buffer into the specified array, but does
 * not update the buffer's internal state (such that subsequent calls to
 * sgs_buffer_peek() or sgs_buffer_write() will reread the same bytes again).
 * Returns 0 on success or -1 if the buffer does not contain enough data to
 * satisfy the request.
 */
int sgs_buffer_peek(const sgs_buffer* buffer, uint8_t* data, size_t len);


/*
 * function: sgs_buffer_read()
 *
 * Copies len bytes of data out of the buffer into the specified array.  Returns
 * 0 on success or -1 if the buffer does not contain enough data to satisfy the
 * request.
 */
int sgs_buffer_read(sgs_buffer* buffer, uint8_t* data, size_t len);

/*
 * function: sgs_buffer_remaining()
 *
 * Returns the amount of space remaining in a buffer (capacity - size).  This is
 * NOT necessarily the number of bytes that can be safely written (contiguously)
 * to the tail of the buffer (since the buffer is circular).  For this function,
 * use sgs_buffer_writable_len().
 */
size_t sgs_buffer_remaining(const sgs_buffer* buffer);

/*
 * function: sgs_buffer_size()
 *
 * Returns the current size of the stored data.
 */
size_t sgs_buffer_size(const sgs_buffer* buffer);

/*
 * function: sgs_buffer_write()
 *
 * Copies data into the buffer as long as the length of the data is less than
 * the remaining capacity of the buffer, returning 0.  Otherwise, returns -1 and
 * errno is set to ENOBUFS.
 */
int sgs_buffer_write(sgs_buffer* buffer, const uint8_t* data, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* !SGS_BUFFER_H */
