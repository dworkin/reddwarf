#ifndef _IDiscoveredUserManager_h
#define _IDiscoveredUserManager_h

class CLIENTAPI IDiscoveredUserManager
{
public:
	virtual std::wstring GetClientClass() const = 0;
	virtual std::wstring GetParameter(const std::wstring& tag) const = 0;
};

#endif
