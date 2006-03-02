#ifndef _ReconnectionKey_h
#define _ReconnectionKey_h

#include "Utilities/GenericID.h"

namespace Darkstar
{
	struct CLIENTAPI ReconnectionKey : public GenericID
	{
		ReconnectionKey() : GenericID() { }
		ReconnectionKey(const ReconnectionKey& other) : GenericID(other) { }
		ReconnectionKey(const std::pair<const byte*, size_t>& data) : GenericID(data) { }
	};
}

#endif
