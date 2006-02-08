#include "stable.h"

#include "DiscoveredGame.h"

DiscoveredGame::DiscoveredGame(int gameID, const std::wstring& name) :
	mID(gameID),
	mName(name)
{
}

DiscoveredGame::~DiscoveredGame()
{
}

std::wstring DiscoveredGame::GetName() const
{
	return mName;
}

int DiscoveredGame::GetID() const
{
	return mID;
}

std::vector<IDiscoveredUserManagerPtr> DiscoveredGame::GetUserManagers() const
{
	return mUserManagers;
}

void DiscoveredGame::AddUserManager(IDiscoveredUserManagerPtr userManager)
{
	mUserManagers.push_back(userManager);
}
