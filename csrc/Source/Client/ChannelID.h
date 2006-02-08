#ifndef _ChannelID_h
#define _ChannelID_h

#include "Utilities/GenericID.h"

struct CLIENTAPI ChannelID : public GenericID
{
	ChannelID() : GenericID() { }
	ChannelID(const ChannelID& other) : GenericID(other) { }
	ChannelID(const std::pair<const byte*, size_t>& data) : GenericID(data) { }
};

#endif
