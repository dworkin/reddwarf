#define STRICT
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <crtdbg.h>

// Core
#include "stable.h"

// Interface
#include "Utilities\Callback.h"

#include "Discovery\IDiscoverer.h"
#include "Discovery\IDiscoveredGame.h"
#include "Discovery\IDiscoveredUserManager.h"

#include "Client\IClientConnectionManager.h"
#include "Client\IClientConnectionManagerListener.h"
#include "Client\IClientChannel.h"
#include "Client\IClientChannelListener.h"

// Implementation
#include "Discovery\URLDiscoverer.h"

#include "Client\ClientConnectionManager.h"

#include "Client\DefaultUserManagerPolicy.h"

using namespace Darkstar;

class ConsoleListener : public IClientConnectionManagerListener, public IClientChannelListener
{
public:
	ConsoleListener(IClientConnectionManager* pCCM) : mpCCM(pCCM) { }

private:
	IClientConnectionManager* mpCCM;
	IClientChannel* mpChannel;

#pragma warning(disable: 4100)

	// IClientConnectionManagerListener
	virtual void OnValidationRequest(const std::vector<ICallback*>& callbacks)
	{
		for (size_t i = 0; i < callbacks.size(); ++i)
		{
			NameCallback* pNCB = dynamic_cast<NameCallback*>(callbacks[i]);
			if (pNCB)
			{
				pNCB->SetName(L"guest2");
			}

			PasswordCallback* pPCB = dynamic_cast<PasswordCallback*>(callbacks[i]);
			if (pPCB)
			{
				pPCB->SetPassword(L"guest2");
			}

			TextInputCallback* pTICB = dynamic_cast<TextInputCallback*>(callbacks[i]);
			if (pTICB) 
			{
			}
		}

		mpCCM->SendValidationResponse(callbacks);
	}

	virtual void OnConnected(const UserID& myID)
	{
		OutputDebugStringW((std::wstring(L"Received User ID ") + myID.ToString() + L"\n").c_str());

		mpCCM->OpenChannel(L"__DCC_Chan");
	}

	virtual void OnConnectionRefused(const std::wstring& message)
	{
		OutputDebugStringW((std::wstring(L"Connection Refused ") + message + L"\n").c_str());
	}

	virtual void OnFailOverInProgress()
	{
		OutputDebugStringW(L"OnFailOverInProgress\n");
	}

	virtual void OnReconnected()
	{
		OutputDebugStringW(L"OnReconnected\n");
	}

	virtual void OnDisconnected()
	{
		OutputDebugStringW(L"OnDisconnected\n");
	}

	virtual void OnUserJoined(const UserID& user)
	{
		OutputDebugStringW((std::wstring(L"OnUserJoined ") + user.ToString() + L"\n").c_str());
	}

	virtual void OnUserLeft(const UserID& user)
	{
		OutputDebugStringW((std::wstring(L"OnUserLeft ") + user.ToString() + L"\n").c_str());
	}

	virtual void OnJoinedChannel(IClientChannel* clientChannel)
	{
		mpChannel = clientChannel;
		OutputDebugStringW((std::wstring(L"OnJoinedChannel ") + clientChannel->GetName() + L"\n").c_str());
		clientChannel->SetListener(this);
	}

	// IClientChannelListener
	virtual void OnPlayerJoined(const UserID& playerID)
	{
		OutputDebugStringW((std::wstring(L"OnPlayerJoined ") + playerID.ToString() + L"\n").c_str());
	}

	virtual void OnPlayerLeft(const UserID& playerID)
	{
		OutputDebugStringW((std::wstring(L"OnPlayerLeft ") + playerID.ToString() + L"\n").c_str());
	}

	virtual void OnDataArrived(const UserID& fromID, const byte* data, size_t length, bool wasReliable)
	{
		OutputDebugStringW((std::wstring(L"OnDataArrived ") + fromID.ToString() + L"\n").c_str());

		mpChannel->SendUnicastData(fromID, data, length, wasReliable);
		mpCCM->SendToServer(data, length, wasReliable);
	}

	virtual void OnChannelClosed()
	{
		OutputDebugStringW((std::wstring(L"OnChannelClosed") + L"\n").c_str());
	}

};

int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int)
{
#ifdef _DEBUG
	_CrtSetDbgFlag(_CRTDBG_LEAK_CHECK_DF | _CrtSetDbgFlag(_CRTDBG_REPORT_FLAG));
#endif

	IDiscoverer* pDiscoverer(new URLDiscoverer(L"C:\\Mind Control\\Sun\\FakeDiscovery.xml"));

	IClientConnectionManager* pCCM = new ClientConnectionManager(L"ChatTest", pDiscoverer, new DefaultUserManagerPolicy());
	IClientConnectionManagerListener* pCCMListener(new ConsoleListener(pCCM));
	pCCM->SetListener(pCCMListener);
	std::vector<std::wstring> classNames = pCCM->GetUserManagerClassNames();
	for (size_t i = 0; i < classNames.size(); ++i)
	{
		if (pCCM->Connect(classNames[i]))
		{
			Sleep(100000);
			pCCM->Disconnect();
		}
	}
	pCCM->SetListener(NULL);

	//std::vector<IDiscoveredGame*> games = pDiscoverer->GetGames();
	//for (size_t i = 0; i < games.size(); ++i)
	//{
	//	MessageBox(NULL, games[i]->GetName().c_str(), L"Discovered Game", MB_OK);
	//	std::vector<IDiscoveredUserManager*> userManagers = games[i]->GetUserManagers();
	//	for (size_t j = 0; j < userManagers.size(); ++j)
	//		MessageBox(NULL, userManagers[j]->GetClientClass().c_str(), games[i]->GetName().c_str(), MB_OK);
	//}

	return 0;
}
