#ifndef _IDiscoveredGame_h
#define _IDiscoveredGame_h

namespace Darkstar
{
	class IDiscoveredUserManager;

	class CLIENTAPI IDiscoveredGame
	{
	public:
		virtual ~IDiscoveredGame() { }

		virtual std::wstring GetName() const = 0;
		virtual int GetID() const = 0;
		virtual std::vector<IDiscoveredUserManager*> GetUserManagers() const = 0;
	};
}

#endif
