#ifndef _DiscoveredUserManager_h
#define _DiscoveredUserManager_h

#include "IDiscoveredUserManager.h"

class DiscoveredUserManager : public IDiscoveredUserManager
{
public:
	DiscoveredUserManager(const std::wstring& clientClass);
	virtual ~DiscoveredUserManager();

	virtual std::wstring GetClientClass() const;
	virtual std::wstring GetParameter(const std::wstring& tag) const;

	void AddParameter(const std::wstring& tag, const std::wstring& value);

private:
	std::wstring mClientClass;
	std::map<std::wstring, std::wstring> mParameters;
};

#endif
