#include "stable.h"

#include "DefaultUserManagerPolicy.h"

#include "Discovery\IDiscoveredGame.h"
#include "Discovery\IDiscoveredUserManager.h"

using namespace Darkstar;

DefaultUserManagerPolicy::DefaultUserManagerPolicy()
{
}

IDiscoveredUserManager* DefaultUserManagerPolicy::Choose(IDiscoveredGame* game, const std::wstring& userManagerName)
{
	std::vector<IDiscoveredUserManager*> managers = game->GetUserManagers();

	int matchingManagerCount = 0;
    for (size_t i = 0; i < managers.size(); ++i)
		if (managers[i]->GetClientClass() == userManagerName)
			++matchingManagerCount;

	if (matchingManagerCount > 0)
	{
		int which = rand() % matchingManagerCount;	// MSED - Bad use of rand since distribution will be incorrect, should move to Mersenne
		for (size_t i = 0; i < managers.size(); ++i, --which)
			if (which == 0)
				return managers[i];
	}

	return NULL;
}
