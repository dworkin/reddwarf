#ifndef _IDiscoverer_h
#define _IDiscoverer_h

class IDiscoveredGame;
typedef boost::shared_ptr<IDiscoveredGame> IDiscoveredGamePtr;

class CLIENTAPI IDiscoverer
{
public:
	virtual std::vector<IDiscoveredGamePtr> GetGames() = 0;
};

TYPEDEF_SMART_PTR(IDiscoverer);

#endif
