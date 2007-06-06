/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 5, 2007
 *
 * This file provides functions relating to communication channels.
 */

#ifndef _CHANNELS_H
#define _CHANNELS_H  1

/*
 * INCLUDES
 */
#include <stdlib.h>    // included for malloc
#include <string.h>    // included for memcpy
#include "CompactId.h"

/*
 * DEFINES
 */


/*
 * TYPEDEFS
 */
// have to do some magic here to declare a self-referential struct
typedef struct SGS_Channel SGS_Channel;

struct SGS_Channel {
  SGS_ID id;
  uint8_t *name;
  uint16_t namelen;
  SGS_Channel *next;
};

typedef struct {
  SGS_Channel *head;
} SGS_ChannelList;

/*
 * FUNCTION DECLARATIONS
 * (implementations are in Channels.c)
 */
void SGS_emptyChannelList(SGS_ChannelList *list);
SGS_Channel *SGS_getChannel(SGS_ChannelList *list, SGS_ID *channel);
void SGS_initChannelList(SGS_ChannelList *list);
int SGS_putChannelIfAbsent(SGS_ChannelList *list, SGS_ID *channel, uint8_t *name, uint16_t namelen);
int SGS_removeChannel(SGS_ChannelList *list, SGS_ID *channel);

#endif
