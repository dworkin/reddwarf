#ifndef _IUserManagerPolicy_h
#define _IUserManagerPolicy_h

namespace SGS
{
	class IDiscoveredGame;
	class IDiscoveredUserManager;

	class CLIENTAPI IUserManagerPolicy
	{
	public:
		virtual ~IUserManagerPolicy() { }

		virtual IDiscoveredUserManager* Choose(IDiscoveredGame* game, const std::wstring& userManagerName) = 0;
	};
}

#endif
