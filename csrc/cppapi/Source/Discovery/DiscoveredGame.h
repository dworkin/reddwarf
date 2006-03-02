#ifndef _DiscoveredGame_h
#define _DiscoveredGame_h

#include "IDiscoveredGame.h"

namespace Darkstar
{
	namespace Internal
	{
		class DiscoveredGame : public IDiscoveredGame
		{
		public:
			DiscoveredGame(int gameID, const std::wstring& name);
			virtual ~DiscoveredGame();

			virtual std::wstring GetName() const;
			virtual int GetID() const;
			virtual std::vector<IDiscoveredUserManager*> GetUserManagers() const;
			void AddUserManager(IDiscoveredUserManager* userManager);

		private:
			int mID;
			std::wstring mName;
			std::vector<IDiscoveredUserManager*> mUserManagers;
		};
	}
}

#endif
