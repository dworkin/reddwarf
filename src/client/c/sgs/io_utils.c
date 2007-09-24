/*
 * This file provides an implementation of a circular byte-buffer.
 */

#include "sgs/io_utils.h"

/*
 * sgs_buffer_read_from_fd()
 */
ssize_t sgs_buffer_read_from_fd(sgs_buffer_impl *buffer, int fd) {
    ssize_t result, total = 0;
    size_t writable = writable_len(buffer);
    
    buffer->eof = 0;  /* Reset flag before any calls to read() */
    
    while (writable > 0) {
        result = read(fd, buffer->buf + tailpos(buffer), writable);
        if (result == -1) return -1;  /* error */
        
        if (result == 0) {   /* EOF */
            buffer->eof = 1;
            return total;
        }
        
        total += result;
        buffer->size += result;
        if (result != writable) return total;  /* partial read */
        writable = writable_len(buffer);
    }
  
    return total;  /* buffer is full */
}

/*
 * sgs_buffer_write_to_fd()
 */
ssize_t sgs_buffer_write_to_fd(sgs_buffer_impl *buffer, int fd) {
    ssize_t result, total = 0;
    size_t readable = readable_len(buffer);
  
    while (readable > 0) {
        result = write(fd, buffer->buf + buffer->position, readable);
        if (result == -1) return -1;  /* error */
        total += result;
        buffer->position = (buffer->position + result) % buffer->capacity;
        buffer->size -= result;
        if (result != readable) return total;  /* partial write */
        readable = readable_len(buffer);
    }
  
    return total;  /* buffer is empty */
}
