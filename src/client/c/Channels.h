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
  // these are declared seperately so that we can use pointer access to the
  //  SGS_ID struct, to be consistent with other structs
  SGS_ID *id;
  SGS_ID idBuf;
  char *name;
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
SGS_Channel *SGS_getChannel(SGS_ChannelList *list, SGS_ID *channelId);
void inline SGS_initChannelList(SGS_ChannelList *list);
SGS_Channel *SGS_nextChannel(SGS_ChannelList *list, SGS_Channel *channel);
int SGS_putChannelIfAbsent(SGS_ChannelList *list, SGS_ID *channelId, char *name, int namelen);
int SGS_removeChannel(SGS_ChannelList *list, SGS_ID *channelId);

#endif
