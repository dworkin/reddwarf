/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 5, 2007
 *
 * This file provides functions relating to communication channels.  This implementation
 *  for managing the set of current channels uses a simple linked list which may be
 *  inefficient for some (many) applications.
 *
 * Conventions:
 *  Unless otherwise noted, all functions with an int return type return
 *  0 upon success and -1 upon failure, while also setting errno to the
 *  specific error code.
 */

#include "Channels.h"

// TODO - delete
#include <stdio.h>

/*
 * TODO
 */
void SGS_emptyChannelList(SGS_ChannelList *list) {
  while (list->head != NULL) {
    SGS_removeChannel(list, &list->head->id);
  }
}

/*
 * TODO
 */
SGS_Channel *SGS_getChannel(SGS_ChannelList *list, SGS_ID *channel) {
  SGS_Channel *ptr = list->head;
  
  while (ptr != NULL) {
    if (SGS_compareCompactIds(&ptr->id, channel) == 0)
	break;
  }
  
  return ptr;
}

/*
 * TODO
 */
void SGS_initChannelList(SGS_ChannelList *list) {
  list->head = NULL;
}

/*
 * TODO
 * 
 * returns:
 *    1: success (channel was inserted into the list; did not previously exist)
 *    0: success (channel already existed in the list; already existed)
 *   -1: failure (errno is set to specific error code)
 */
int SGS_putChannelIfAbsent(SGS_ChannelList *list, SGS_ID *channel, uint8_t *name, uint16_t namelen) {
  SGS_Channel *ptr = list->head;
  SGS_Channel *prevNode = NULL;
  
  // iterate to the end of the list, returning if the channel found to already exist
  while (ptr != NULL) {
    if (SGS_compareCompactIds(&ptr->id, channel))
      return 0;
    
    prevNode = ptr;
    ptr = ptr->next;
  }
  
  // channel does not already exist, so insert it
  if (prevNode == NULL) {
    list->head = (SGS_Channel *)malloc(sizeof(SGS_Channel));
    ptr = list->head;
  }
  else {
    prevNode->next = (SGS_Channel *)malloc(sizeof(SGS_Channel));
    ptr = prevNode->next;
  }
  
  if (ptr == NULL) {
    errno = ENOMEM;
    return -1;
  }
  
  ptr->name = (uint8_t*)malloc(namelen);
  
  if (ptr->name == NULL) {
    // make sure to "roll back" the memory allocation for the SGS_Channel itself
    free(ptr);
    errno = ENOMEM;
    return -1;
  }
  
  memcpy(&ptr->id, channel, sizeof(SGS_ID));
  memcpy(ptr->name, name, namelen);
  ptr->namelen = namelen;
  ptr->next = NULL;
  
  return 1;
}

/*
 * TODO
 */
int SGS_removeChannel(SGS_ChannelList *list, SGS_ID *channel) {
  SGS_Channel *ptr = list->head;
  SGS_Channel *prevNode = NULL;
  
  while (ptr != NULL) {
    if (SGS_compareCompactIds(&ptr->id, channel) == 0) {
      // update the "next" pointer from the previous node to skip over this node
      if (prevNode == NULL)
	list->head = ptr->next;
      else
	prevNode->next = ptr->next;
      
      // delete this node
      free(ptr->name);
      free(ptr);
    }
    
    prevNode = ptr;
    ptr = ptr->next;
    
    return 0;
  }
  
  return -1;
}

