/*
 * This file provides an implementation of a circular byte-buffer.
 */

#include "sgs/config.h"
#include "sgs/private/io_utils.h"
#include "sgs/private/buffer_impl.h"

/*
 * sgs_impl_read_from_fd()
 */
ssize_t sgs_impl_read_from_fd(sgs_buffer_impl *buffer, int fd) {
    ssize_t result, total = 0;
    size_t writable = writable_len(buffer);
    
    while (writable > 0) {
        result = read(fd, buffer->buf + tailpos(buffer), writable);
        if (result == -1) return -1;  /* error */
        
        if (result == 0) {   /* EOF */
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
 * sgs_impl_write_to_fd()
 */
ssize_t sgs_impl_write_to_fd(sgs_buffer_impl *buffer, int fd) {
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
