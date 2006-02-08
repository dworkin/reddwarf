#ifndef _DefaultUserManagerPolicy_h
#define _DefaultUserManagerPolicy_h

#include "IUserManagerPolicy.h"

class CLIENTAPI DefaultUserManagerPolicy : public IUserManagerPolicy
{
public:
	DefaultUserManagerPolicy();

	virtual IDiscoveredUserManagerPtr Choose(IDiscoveredGamePtr game, const std::wstring& userManagerName);
};

#endif
