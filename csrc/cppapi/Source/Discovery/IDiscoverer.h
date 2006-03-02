#ifndef _IDiscoverer_h
#define _IDiscoverer_h

namespace Darkstar
{
	class IDiscoveredGame;

	class CLIENTAPI IDiscoverer
	{
	public:
		virtual ~IDiscoverer() { }

		virtual std::vector<IDiscoveredGame*> GetGames() = 0;
	};
}

#endif
