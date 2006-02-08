#ifndef _DiscoveryXMLHandler_h
#define _DiscoveryXMLHandler_h

#include "XMLParser/ISAXHandler.h"

class IDiscoveredGame;
typedef boost::shared_ptr<IDiscoveredGame> IDiscoveredGamePtr;

class DiscoveredGame;
typedef boost::shared_ptr<DiscoveredGame> DiscoveredGamePtr;

class DiscoveredUserManager;
typedef boost::shared_ptr<DiscoveredUserManager> DiscoveredUserManagerPtr;

class DiscoveryXMLHandler : public ISAXHandler
{
public:
	virtual std::vector<IDiscoveredGamePtr> GetGames() const;

private:
	virtual void OnStartElement(const std::wstring& name, AttributeMap& attributes);
	virtual void OnEndElement(const std::wstring& name);

	std::vector<IDiscoveredGamePtr> mGames;

	DiscoveredGamePtr mGame;
	DiscoveredUserManagerPtr mUserManager;
};

#endif
