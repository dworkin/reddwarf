#include "stable.h"

#include "URLDiscoverer.h"

#include "XMLParser/XMLReader.h"

#include "Platform/Platform.h"
#include "Platform/IStream.h"

#include "DiscoveredGame.h"
#include "DiscoveredUserManager.h"

#if defined(SN_TARGET_PSP_HW)
int _wtoi(const wchar_t*)
{
	throw std::exception();
	//return 0;
}
#endif

using namespace SGS;
using namespace SGS::Internal;

URLDiscoverer::URLDiscoverer(const std::wstring& url) :
	mURL(url)
{
}

URLDiscoverer::~URLDiscoverer()
{
}

std::vector<IDiscoveredGame*> URLDiscoverer::GetGames()
{
	std::vector<IDiscoveredGame*> games;
	DiscoveredGame* pGame = NULL;
	DiscoveredUserManager* pUserManager = NULL;

	IStream* pStream = Platform::OpenStream(mURL.c_str());
	if (pStream != NULL)
	{
		XMLReader reader(pStream);
		while (!reader.IsEOF())
		{
			XMLElement element = reader.ReadElement();
			if (element.kind == kStart)
			{
				if (element.name == L"DISCOVERY") 
				{	// start of discovery document														
					games.clear();
				} 
				else if (element.name == L"GAME")
				{	// start of game record
					pGame = new DiscoveredGame(_wtoi(element.attributes[L"id"].c_str()), element.attributes[L"name"]);
				}
				else if (element.name == L"USERMANAGER")
				{
					pUserManager = new DiscoveredUserManager(element.attributes[L"clientclass"]);
				} 
				else if (element.name == L"PARAMETER")
				{
					pUserManager->AddParameter(element.attributes[L"tag"], element.attributes[L"value"]);
				}
			}
			else if (element.kind == kEnd)
			{
				if (element.name == L"DISCOVERY") 
				{
					// no action needed
				} 
				else if (element.name == L"GAME")
				{	// end of game record
					games.push_back(pGame);
					pGame = NULL;
					pUserManager = NULL;
				} 
				else if (element.name == L"USERMANAGER")
				{
					pGame->AddUserManager(pUserManager);
					pUserManager = NULL;
				}
				else if (element.name == L"PARAMETER")
				{
					// no action needed
				}
			}
		}

		delete pStream;
	}

	return games;
}
