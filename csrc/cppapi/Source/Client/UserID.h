#ifndef _UserID_h
#define _UserID_h

#include "Utilities/GenericID.h"

namespace Darkstar
{
	struct CLIENTAPI UserID : public GenericID
	{
		UserID() : GenericID() { }
		UserID(const UserID& other) : GenericID(other) { }
		UserID(const std::pair<const byte*, size_t>& data) : GenericID(data) { }
	};
}

#endif
