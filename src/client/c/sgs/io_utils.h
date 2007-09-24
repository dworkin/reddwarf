
#ifndef SGS_IO_UTILS_H
#define SGS_IO_UTILS_H 1

#include "sgs/config.h"
#include "sgs/buffer.h"

/*
 * function: sgs_buffer_read_from_fd()
 *
 * Copies data from the specified file descriptor into the buffer.  Copying
 * stops if (a) the buffer runs out of room, or (b) a call to read() on the file
 * descriptor returns any value other than the requested length.  If copying
 * stops because the buffer ran out of room or because a call to read() returned
 * a valued smaller than the requested read size, then the total number of bytes
 * read into the buffer is returned.  Otherwise, if read ever returns -1,
 * indicating an error, then -1 is returned.  Note that if this method returns 0
 * it may indicate that the buffer is full, or that read() returned 0,
 * indicating that end-of-file was read.  The method sgs_buffer_eof() can be
 * used to disambiguate these two cases.
 */
ssize_t sgs_buffer_read_from_fd(sgs_buffer* buffer, int fd);

/*
 * function: sgs_buffer_write_to_fd()
 *
 * Copies len bytes of data out of the buffer and writes them to the specified
 * file descriptor.  Writing stops if (a) the buffer runs out of data, or (b) a
 * call to write() on the file descriptor returns any value other than the
 * requested length.  Returns -1 if an error occurs; otherwise returns the total
 * number of bytes written to the file descriptor.
 */
ssize_t sgs_buffer_write_to_fd(sgs_buffer* buffer, int fd);

#endif /* !SGS_IO_UTILS_H */
