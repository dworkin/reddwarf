#ifndef _DefaultUserManagerPolicy_h
#define _DefaultUserManagerPolicy_h

#include "IUserManagerPolicy.h"

namespace SGS
{
	class CLIENTAPI DefaultUserManagerPolicy : public IUserManagerPolicy
	{
	public:
		DefaultUserManagerPolicy();

		virtual IDiscoveredUserManager* Choose(IDiscoveredGame* game, const std::wstring& userManagerName);
	};
}

#endif
