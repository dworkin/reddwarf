#include "stable.h"

#include "DiscoveredUserManager.h"

using namespace SGS;
using namespace SGS::Internal;

DiscoveredUserManager::DiscoveredUserManager(const std::wstring& clientClass) :
	mClientClass(clientClass)
{
}

DiscoveredUserManager::~DiscoveredUserManager()
{
}

std::wstring DiscoveredUserManager::GetClientClass() const
{
	return mClientClass;
}

std::wstring DiscoveredUserManager::GetParameter(const std::wstring& tag) const
{
	std::map<std::wstring, std::wstring>::const_iterator iter = mParameters.find(tag);
	if (iter != mParameters.end())
		return iter->second;
	else
		return L"";
}

void DiscoveredUserManager::AddParameter(const std::wstring& tag, const std::wstring& value)
{
	mParameters[tag] = value;
}
