/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * This file provides an implementation of a circular byte-buffer.
 */

#include <stdlib.h>
#include <string.h>
#include "sgs_buffer.h"

/*
 * STATIC FUNCTION DECLARATIONS
 * (can only be called by functions in this file)
 */
static int realign(sgs_buffer buffer);
static size_t readable_len(const sgs_buffer buffer);
static size_t writable_len(const sgs_buffer buffer);

/*
 * (EXTERNAL) FUNCTION IMPLEMENTATIONS
 */

/*
 * sgs_buffer_can_read()
 */
int sgs_buffer_can_read(sgs_buffer buffer, size_t len) {
  if (sgs_buffer_size(buffer) < len)
    return 0;
  
  if (readable_len(buffer) < len) {
    if (realign(buffer) == -1) return 0;
  }
  
  return 1;
}

/*
 * sgs_buffer_can_write()
 */
int sgs_buffer_can_write(sgs_buffer buffer, size_t len) {
  if (sgs_buffer_remaining_capacity(buffer) < len)
    return 0;
  
  if (writable_len(buffer) < len) {
    if (realign(buffer) == -1) return 0;
  }
  
  return 1;
}

/*
 * sgs_buffer_capacity()
 */
size_t sgs_buffer_capacity(sgs_buffer buffer) {
  return buffer->capacity;
}

/*
 * sgs_buffer_create()
 */
sgs_buffer sgs_buffer_create(size_t capacity) {
  sgs_buffer buffer;
  
  buffer = (sgs_buffer)malloc(sizeof(struct sgs_buffer));
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
  
  return buffer;
}

/*
 * sgs_buffer_destroy()
 */
void sgs_buffer_destroy(sgs_buffer buffer) {
  free(buffer->buf);
  free(buffer);
}

/*
 * sgs_buffer_empty()
 */
void sgs_buffer_empty(sgs_buffer buffer) {
  buffer->position = 0;  /** not necessary, but convenient */
  buffer->size = 0;
}

/*
 * sgs_buffer_head()
 */
uint8_t *sgs_buffer_head(sgs_buffer buffer) {
  return buffer->buf + buffer->position;
}

/*
 * sgs_buffer_mark()
 */
void sgs_buffer_mark(sgs_buffer buffer) {
  buffer->marked_position = buffer->position;
  buffer->marked_size = buffer->size;
}

/*
 * sgs_buffer_read_update()
 */
int sgs_buffer_read_update(sgs_buffer buffer, size_t len) {
  /* Check for underflow. */
  if (len > readable_len(buffer)) return -1;
  
  /** Advance head pointer to new position. */
  buffer->position = (buffer->position + len) % buffer->capacity;
  buffer->size -= len;
  
  /** Note necessary, but convenient and more efficient... */
  if (buffer->size == 0) buffer->position = 0;
  
  return 0;
}

/*
 * sgs_buffer_reset()
 */
void sgs_buffer_reset(sgs_buffer buffer) {
  buffer->position = buffer->marked_position;
  buffer->size = buffer->marked_size;
}

/*
 * sgs_buffer_remaining_capacity()
 */
size_t sgs_buffer_remaining_capacity(sgs_buffer buffer) {
  return buffer->capacity - buffer->size;
}

/*
 * sgs_buffer_size()
 */
size_t sgs_buffer_size(sgs_buffer buffer) {
  return buffer->size;
}

/*
 * sgs_buffer_tail()
 */
uint8_t *sgs_buffer_tail(sgs_buffer buffer) {
  return buffer->buf + (buffer->position + buffer->size) % buffer->capacity;
}

/*
 * sgs_buffer_write_update()
 */
int sgs_buffer_write_update(sgs_buffer buffer, size_t len) {
  if (len > writable_len(buffer)) return -1;
  
  buffer->size += len;
  return 0;
}

/*
 * INTERNAL (STATIC) FUNCTION IMPLEMENTATIONS
 */

/*
 * realign()
 */
static int realign(sgs_buffer buffer) {
  size_t tailpos = (buffer->position + buffer->size) % buffer->capacity;
  uint8_t *copybuf;
  size_t readable = readable_len(buffer);
  
  if (tailpos >= buffer->position) {
    /** The stored data has not wrapped yet. */
    memmove(buffer->buf, buffer->buf + buffer->position, buffer->size);
  } else {
    /**
     * The stored data HAS wrapped, which makes this trickier; an easy solution
     * is to just reallocate the buffer and copy over the data.
     */
    copybuf = (uint8_t*)malloc(buffer->capacity);
    if (copybuf == NULL) return -1;
    
    memcpy(copybuf, buffer->buf + buffer->position, readable);
    memcpy(copybuf + readable, buffer->buf, tailpos);
    
    free(buffer->buf);
    buffer->buf = copybuf;
  }
  
  return 0;
}

/*
 * readable_len()
 */
static size_t readable_len(const sgs_buffer buffer) {
  size_t tailpos = (buffer->position + buffer->size) % buffer->capacity;
  
  if (tailpos >= buffer->position) {
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
 * writable_len()
 */
static size_t writable_len(const sgs_buffer buffer) {
  size_t tailpos = (buffer->position + buffer->size) % buffer->capacity;
  
  if (tailpos >= buffer->position) {
    /*
     * The stored data has not wrapped yet, so we can write until we reach the
     * end of the memory block.
     */
    return buffer->capacity - tailpos;
  } else {
    /** The stored data HAS wrapped, so we can write until we reach the head. */
    return buffer->position - tailpos;
  }
}
