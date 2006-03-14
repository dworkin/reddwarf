#ifndef _URLDiscoverer_h
#define _URLDiscoverer_h

#include "IDiscoverer.h"

namespace SGS
{
	class CLIENTAPI URLDiscoverer : public IDiscoverer
	{
	public:
		URLDiscoverer(const std::wstring& url);
		virtual ~URLDiscoverer();

		virtual std::vector<IDiscoveredGame*> GetGames();

	private:
		std::wstring mURL;
	};
}

#endif
