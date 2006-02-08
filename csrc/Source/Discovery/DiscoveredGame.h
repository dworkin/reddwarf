#ifndef _DiscoveredGame_h
#define _DiscoveredGame_h

#include "IDiscoveredGame.h"

class DiscoveredGame : public IDiscoveredGame
{
public:
	DiscoveredGame(int gameID, const std::wstring& name);
	virtual ~DiscoveredGame();

	virtual std::wstring GetName() const;
	virtual int GetID() const;
	virtual std::vector<IDiscoveredUserManagerPtr> GetUserManagers() const;
	void AddUserManager(IDiscoveredUserManagerPtr userManager);

private:
	int mID;
	std::wstring mName;
	std::vector<IDiscoveredUserManagerPtr> mUserManagers;
};

#endif
