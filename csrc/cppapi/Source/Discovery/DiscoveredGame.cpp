#include "stable.h"

#include "DiscoveredGame.h"

#include "IDiscoveredUserManager.h"

using namespace Darkstar;
using namespace Darkstar::Internal;

DiscoveredGame::DiscoveredGame(int gameID, const std::wstring& name) :
	mID(gameID),
	mName(name)
{
}

DiscoveredGame::~DiscoveredGame()
{
	for (size_t i = 0; i < mUserManagers.size(); ++i)
		delete mUserManagers[i];
}

std::wstring DiscoveredGame::GetName() const
{
	return mName;
}

int DiscoveredGame::GetID() const
{
	return mID;
}

std::vector<IDiscoveredUserManager*> DiscoveredGame::GetUserManagers() const
{
	return mUserManagers;
}

void DiscoveredGame::AddUserManager(IDiscoveredUserManager* userManager)
{
	mUserManagers.push_back(userManager);
}
