#include "stable.h"

#include "DiscoveryXMLHandler.h"

#include "DiscoveredGame.h"
#include "DiscoveredUserManager.h"

std::vector<IDiscoveredGamePtr> DiscoveryXMLHandler::GetGames() const
{
	return mGames;
}

void DiscoveryXMLHandler::OnStartElement(const std::wstring& name, AttributeMap& attributes)
{
	if (name == L"DISCOVERY") 
	{	// start of discovery document														
		mGames.clear();
	} 
	else if (name == L"GAME")
	{	// start of game record
		mGame.reset(new DiscoveredGame(_wtoi(attributes[L"id"].c_str()), attributes[L"name"]));
	}
	else if (name == L"USERMANAGER")
	{
		mUserManager.reset(new DiscoveredUserManager(attributes[L"clientclass"]));
	} 
	else if (name == L"PARAMETER")
	{
		mUserManager->AddParameter(attributes[L"tag"], attributes[L"value"]);
	}
}

void DiscoveryXMLHandler::OnEndElement(const std::wstring& name)
{
	if (name == L"DISCOVERY") 
	{
		// no action needed
	} 
	else if (name == L"GAME")
	{	// end of game record
		mGames.push_back(mGame);
		mGame.reset();
		mUserManager.reset();
	} 
	else if (name == L"USERMANAGER")
	{
		mGame->AddUserManager(boost::dynamic_pointer_cast<IDiscoveredUserManager>(mUserManager));
		mUserManager.reset();
	}
	else if (name == L"PARAMETER")
	{
		// no action needed
	}
}
