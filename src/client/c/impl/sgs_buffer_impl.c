/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 *
 * THIS PRODUCT CONTAINS CONFIDENTIAL INFORMATION AND TRADE SECRETS OF SUN
 * MICROSYSTEMS, INC. USE, DISCLOSURE OR REPRODUCTION IS PROHIBITED WITHOUT
 * THE PRIOR EXPRESS WRITTEN PERMISSION OF SUN MICROSYSTEMS, INC.
 */

/*
 * This file provides an implementation of a circular byte-buffer.
 */

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "sgs_buffer.h"
#include "sgs_buffer_impl.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static size_t readable_len(const sgs_buffer_impl *buffer);
static size_t tailpos (const sgs_buffer_impl *buffer);
static size_t writable_len(const sgs_buffer_impl *buffer);

/*
 * FUNCTION IMPLEMENTATIONS FOR SGS_BUFFER.H
 */

/*
 * sgs_buffer_capacity()
 */
size_t sgs_buffer_capacity(const sgs_buffer_impl *buffer) {
    return buffer->capacity;
}

/*
 * sgs_buffer_empty()
 */
void sgs_buffer_empty(sgs_buffer_impl *buffer) {
    buffer->position = 0;  /** not necessary, but convenient */
    buffer->size = 0;
}

/*
 * sgs_buffer_eof()
 */
int sgs_buffer_eof(const sgs_buffer_impl *buffer) {
    return buffer->eof;
}

/*
 * sgs_buffer_free()
 */
void sgs_buffer_free(sgs_buffer_impl *buffer) {
    free(buffer->buf);
    free(buffer);
}

/*
 * sgs_buffer_new()
 */
sgs_buffer_impl *sgs_buffer_new(size_t capacity) {
    sgs_buffer_impl *buffer;
  
    buffer = (sgs_buffer_impl*)malloc(sizeof(struct sgs_buffer_impl));
    if (buffer == NULL) return NULL;
  
    buffer->buf = (uint8_t*)malloc(capacity);
  
    if (buffer->buf == NULL) {
        /** "roll back" allocation of memory for buffer struct itself. */
        free(buffer);
        return NULL;
    }
  
    buffer->capacity = capacity;
    buffer->position = 0;
    buffer->size = 0;
    buffer->eof = 0;
  
    return buffer;
}

/*
 * sgs_buffer_peek()
 */
int sgs_buffer_peek(const sgs_buffer_impl *buffer, uint8_t *data, size_t len) {
    size_t readable = readable_len(buffer);
  
    if (len > buffer->size) return -1;
  
    if (readable >= len) {
        memcpy(data, buffer->buf + buffer->position, len);
    } else {
        memcpy(data, buffer->buf + buffer->position, readable);
        memcpy(data + readable, buffer->buf, len - readable);
    }
  
    return 0;
}

/*
 * sgs_buffer_read()
 */
int sgs_buffer_read(sgs_buffer_impl *buffer, uint8_t *data, size_t len) {
    if (sgs_buffer_peek(buffer, data, len) == -1) return -1;
  
    buffer->position = (buffer->position + len) % buffer->capacity;
    buffer->size -= len;
    return 0;
}

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
 * sgs_buffer_remaining_capacity()
 */
size_t sgs_buffer_remaining_capacity(const sgs_buffer_impl *buffer) {
    return buffer->capacity - buffer->size;
}

/*
 * sgs_buffer_size()
 */
size_t sgs_buffer_size(const sgs_buffer_impl *buffer) {
    return buffer->size;
}

/*
 * sgs_buffer_write()
 */
int sgs_buffer_write(sgs_buffer_impl *buffer, const uint8_t *data, size_t len) {
    size_t writable = writable_len(buffer);
    
    if (len > sgs_buffer_remaining_capacity(buffer)) {
        errno = ENOBUFS;
        return -1;
    }
    
    if (writable >= len) {
        memcpy(buffer->buf + tailpos(buffer), data, len);
    } else {
        memcpy(buffer->buf + tailpos(buffer), data, writable);
        memcpy(buffer->buf, data + writable, len - writable);
    }
    
    buffer->size += len;
    return 0;
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

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 */

/*
 * readable_len()
 */
static size_t readable_len(const sgs_buffer_impl *buffer) {
    if (buffer->size == 0) return 0;
  
    if (tailpos(buffer) > buffer->position) {
        /*
         * The stored data has not wrapped yet, so we can read until we read the
         * tail.
         */
        return buffer->size;
    } else {
        /** 
         * The stored data HAS wrapped, we can we read until we reach the end of
         * the memory block.
         */
        return buffer->capacity - buffer->position;
    }
}

/*
 * tailpos()
 */
static size_t tailpos(const sgs_buffer_impl *buffer) {
    return (buffer->position + buffer->size) % buffer->capacity;
}

/*
 * writable_len()
 */
static size_t writable_len(const sgs_buffer_impl *buffer) {
    size_t mytailpos = tailpos(buffer);
  
    if (mytailpos >= buffer->position) {
        /*
         * The stored data has not wrapped yet, so we can write until we reach
         * the end of the memory block.
         */
        return buffer->capacity - mytailpos;
    } else {
        /**
         * The stored data HAS wrapped, so we can write until we reach the
         * head.
        */
        return buffer->position - mytailpos;
    }
}
