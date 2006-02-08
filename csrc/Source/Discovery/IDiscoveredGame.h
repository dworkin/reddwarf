#ifndef _IDiscoveredGame_h
#define _IDiscoveredGame_h

class IDiscoveredUserManager;
typedef boost::shared_ptr<IDiscoveredUserManager> IDiscoveredUserManagerPtr;

class CLIENTAPI IDiscoveredGame
{
public:
	virtual std::wstring GetName() const = 0;
	virtual int GetID() const = 0;
	virtual std::vector<IDiscoveredUserManagerPtr> GetUserManagers() const = 0;
};

#endif
