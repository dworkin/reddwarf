#ifndef _IUserManagerPolicy_h
#define _IUserManagerPolicy_h

TYPEDEF_SMART_PTR(IDiscoveredGame);
TYPEDEF_SMART_PTR(IDiscoveredUserManager);

class CLIENTAPI IUserManagerPolicy
{
public:
	virtual IDiscoveredUserManagerPtr Choose(IDiscoveredGamePtr game, const std::wstring& userManagerName) = 0;
};

#endif
