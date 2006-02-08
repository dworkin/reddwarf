#ifndef _URLDiscoverer_h
#define _URLDiscoverer_h

#include "IDiscoverer.h"

class CLIENTAPI URLDiscoverer : public IDiscoverer
{
public:
	URLDiscoverer(const std::wstring& url);
	virtual ~URLDiscoverer();

	virtual std::vector<IDiscoveredGamePtr> GetGames();

private:
	std::wstring mURL;
};

#endif
