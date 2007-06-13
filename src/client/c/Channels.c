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

/*
 * function: SGS_emptyChannelList()
 *
 * Removes all elements from the specified channel list.
 */
void SGS_emptyChannelList(SGS_ChannelList *list) {
  while (list->head != NULL) {
    SGS_removeChannel(list, list->head->id);
  }
}

/*
 * function: SGS_getChannel()
 *
 * Returns a pointer to the specified channel if it exists in the specified list;
 *  otherwise returns NULL.
 */
SGS_Channel *SGS_getChannel(SGS_ChannelList *list, SGS_ID *channelId) {
  SGS_Channel *ptr = list->head;
  
  while (ptr != NULL) {
    if (SGS_compareCompactIds(ptr->id, channelId) == 0)
	break;
    
    ptr = ptr->next;
  }
  
  return ptr;
}

/*
 * function: SGS_initChannelList()
 *
 * Initializes the fields of a new channel list.
 */
void SGS_initChannelList(SGS_ChannelList *list) {
  list->head = NULL;
}

/*
 * function: SGS_nextChannel()
 * 
 * Functions as a simple iterator over a channel list; given a specified channel,
 *  if that channel exists in the list, a pointer to the next element (immediately
 *  following the specified channel in the list) is returned.  If NULL is passed
 *  as the specified channel, the first element of the list is returned.  If
 *  the specified channel is the last element in the list or if it does not exist
 *  in the list at all, NULL is returned.
 */
SGS_Channel *SGS_nextChannel(SGS_ChannelList *list, SGS_Channel *channel) {
  SGS_Channel *ptr;
  
  if (channel == NULL) return list->head;
  
  ptr = SGS_getChannel(list, channel->id);
  if (ptr == NULL) return NULL;
  
  return ptr->next;
}

/*
 * function: SGS_putChannelIfAbsent()
 *
 * Inserts a new element into the list to represent the specified channel,  unless an element
 *  already exists in the list to represent the specified channel.
 *
 * args:
 *       list: the channel list to insert into
 *  channelId: ID of the channel to insert
 *       name: name of the channel to insert (does not need to be null-terminated)
 *    namelen: length of the name of the channel to insert
 * 
 * returns:
 *    1: success (channel was inserted into the list; did not previously exist)
 *    0: success (channel already existed in the list; already existed)
 *   -1: failure (errno is set to specific error code)
 */
int SGS_putChannelIfAbsent(SGS_ChannelList *list, SGS_ID *channelId, char *name, int namelen) {
  SGS_Channel *ptr = list->head;
  SGS_Channel *prevNode = NULL;
  
  // iterate to the end of the list, returning if the channel is found to already exist
  while (ptr != NULL) {
    if (SGS_compareCompactIds(ptr->id, channelId) == 0)
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
  
  ptr->name = (char*)malloc(namelen + 1);
  
  if (ptr->name == NULL) {
    // make sure to "roll back" the memory allocation for the SGS_Channel itself
    free(ptr);
    errno = ENOMEM;
    return -1;
  }
  
  ptr->id = &ptr->idBuf;
  memcpy(ptr->id, channelId, sizeof(SGS_ID));
  memcpy(ptr->name, name, namelen);
  ptr->name[namelen] = '\0';
  ptr->next = NULL;
  
  return 1;
}

/*
 * function: SGS_removeChannel()
 *
 * Removes the specified channel from the list.
 *
 * returns:
 *    0: success
 *   -1: failure (channel does not exist in the list)
 */
int SGS_removeChannel(SGS_ChannelList *list, SGS_ID *channelId) {
  SGS_Channel *ptr = list->head;
  SGS_Channel *prevNode = NULL;
  
  while (ptr != NULL) {
    if (SGS_compareCompactIds(ptr->id, channelId) == 0) {
      // update the "next" pointer from the previous node to skip over this node
      if (prevNode == NULL)
	list->head = ptr->next;
      else
	prevNode->next = ptr->next;
      
      // delete this node
      free(ptr->name);
      free(ptr);
      
      return 0;
    }
    
    prevNode = ptr;
    ptr = ptr->next;
  }
  
  return -1;
}
