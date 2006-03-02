#include "stable.h"

#include "ClientConnectionManager.h"

#include "ClientAlreadyConnectedException.h"
#include "ClientChannel.h"

#include "IClientConnectionManagerListener.h"
#include "IUserManagerClient.h"
#include "IUserManagerPolicy.h"

#include "Discovery/IDiscoverer.h"
#include "Discovery/IDiscoveredGame.h"
#include "Discovery/IDiscoveredUserManager.h"

#include "Platform/Platform.h"

using namespace Darkstar;
using namespace Darkstar::Internal;

ClientConnectionManager::ClientConnectionManager(const std::wstring& gameName, IDiscoverer* discoverer, IUserManagerPolicy* policy) :
	mDiscoverer(discoverer),
	mPolicy(policy),
	mUserManager(NULL),
	mListener(NULL),
	mGameName(gameName),
	mReconnecting(false),
	mConnected(false),
	mKeyTimeoutMS(0),
	mConnAttempts(0),
	mConnAttemptCounter(0),
	mConnWaitMS(0),
	mExiting(false)
{
}

ClientConnectionManager::~ClientConnectionManager()
{
	for (std::map<ChannelID, ClientChannel*>::iterator iter = mChannelMap.begin(); iter != mChannelMap.end(); ++iter)
		delete iter->second;

	if (mUserManager != NULL)
		Platform::DestroyUserManagerClient(mUserManager);
}

void ClientConnectionManager::SetListener(IClientConnectionManagerListener* clientConnectionManagerListener)
{
	mListener = clientConnectionManagerListener;
}

std::vector<std::wstring> ClientConnectionManager::GetUserManagerClassNames()
{
	std::vector<std::wstring> names;

	std::auto_ptr<IDiscoveredGame> game(discoverGame(mGameName));
	if (game.get() == NULL)
		return names;

	std::vector<IDiscoveredUserManager*> userManagers = game->GetUserManagers();
	names.reserve(userManagers.size());
	for (size_t i = 0; i < userManagers.size(); ++i)
		if (std::find(names.begin(), names.end(), userManagers[i]->GetClientClass()) == names.end())
			names.push_back(userManagers[i]->GetClientClass());

	return names;
}
bool ClientConnectionManager::Connect(const std::wstring& userManagerClassName)
{
	int attempts = 10;
	//MSED - Need to provide a method for retreiving properties
	//String attstr = System.getProperty("sgs.clientconnmgr.connattempts");
	//if (attstr != null) {
	//	attempts = Integer.parseInt(attstr);
	//}
	int sleepTime = 100;
	//String sleepstr = System.getProperty("sgs.clientconnmgr.connwait");
	//if (sleepstr != null) {
	//	sleepTime = Long.parseLong(sleepstr);
	//}
	return Connect(userManagerClassName, attempts, sleepTime);
}

bool ClientConnectionManager::Connect(const std::wstring& userManagerClassName, int connectAttempts, int msBetweenAttempts)
{
	if (mConnected) 
		throw ClientAlreadyConnectedException("bad attempt to connect when already connected.");

	mUserManagerClassName = userManagerClassName;
	mConnAttempts = connectAttempts;
	mConnAttemptCounter = 0;
	mConnWaitMS = msBetweenAttempts;
	mReconnecting = false;
	return connect();
}

void ClientConnectionManager::Disconnect()
{
	mExiting = true;
	if (mUserManager != NULL)
		mUserManager->Logout();
}

void ClientConnectionManager::SendValidationResponse(const std::vector<ICallback*>& callbacks)
{
	mUserManager->ValidationDataResponse(callbacks);
}

void ClientConnectionManager::SendToServer(const byte* data, size_t length, bool isReliable)
{
	mUserManager->SendToServer(data, length, isReliable);
}

void ClientConnectionManager::OpenChannel(const std::wstring& channelName)
{
	mUserManager->JoinChannel(channelName);
}

bool ClientConnectionManager::IsServerID(const UserID& userID)
{
	return userID == mServerID;
}

void ClientConnectionManager::CloseChannel(const ChannelID& channelID)
{
	mUserManager->LeaveChannel(channelID);
}

void ClientConnectionManager::Update()
{
	if (mUserManager)
		mUserManager->Update();
}

void ClientConnectionManager::OnConnected()
{
	mConnected = true;
	if (!mReconnecting || mKeyTimeoutMS < Platform::GetSystemTimeMS())
		mUserManager->Login();
	else
		mUserManager->ReconnectLogin(mMyID, mReconnectionKey);
}

void ClientConnectionManager::OnDisconnected()
{
	if (mConnected == false) 
	{	// not yet connected
		if (mConnAttemptCounter < mConnAttempts)
		{	// try again
			Platform::Sleep(mConnWaitMS);
			connect();
		} 
		else 
		{
			mListener->OnDisconnected();
		}
	} 
	else 
	{	// lost connection
		if (!mExiting && mKeyTimeoutMS > Platform::GetSystemTimeMS())
		{	// valid reconnection key
			mListener->OnFailOverInProgress();
			mReconnecting = true;
			connect();
		} 
		else 
		{	// we cant fail over
			mConnected = false;
			mListener->OnDisconnected();
		}
	}
}

void ClientConnectionManager::OnNewConnectionKeyIssued(const ReconnectionKey& key, int64 ttl)
{
	mReconnectionKey = key;
	Platform::Log((std::wstring(L"Received Reconnection Key ") + mReconnectionKey.ToString() + L"\n").c_str());
	mKeyTimeoutMS = (int)(Platform::GetSystemTimeMS() + (ttl * 1000));	// MSED - Truncating the TTL from int64 to int32
}

void ClientConnectionManager::OnValidationDataRequest(const std::vector<ICallback*>& callbacks)
{
	mListener->OnValidationRequest(callbacks);
}

void ClientConnectionManager::OnLoginAccepted(const UserID& userID)
{
	mMyID = userID;
	mListener->OnConnected(mMyID);
}

void ClientConnectionManager::OnLoginRejected(const std::wstring& message)
{
	mListener->OnConnectionRefused(message);
}

void ClientConnectionManager::OnUserAdded(const UserID& userID)
{
	mListener->OnUserJoined(userID);
}

void ClientConnectionManager::OnUserDropped(const UserID& userID)
{
	mListener->OnUserLeft(userID);
}

void ClientConnectionManager::OnChannelLocked(const std::wstring& name, const UserID& userID)
{
	mListener->OnChannelLocked(name, userID);
}

void ClientConnectionManager::OnJoinedChannel(const std::wstring& name, const ChannelID& channelID)
{
	ClientChannel* clientChannel = new ClientChannel(this, name, channelID);
	mChannelMap[channelID] = clientChannel;
	mListener->OnJoinedChannel(clientChannel);
}

void ClientConnectionManager::OnLeftChannel(const ChannelID& channelID)
{
	ClientChannel* clientChannel = mChannelMap[channelID];
	clientChannel->OnChannelClosed();
	delete clientChannel;
	mChannelMap.erase(channelID);
}

void ClientConnectionManager::OnUserJoinedChannel(const ChannelID& channelID, const UserID& userID)
{
	mChannelMap[channelID]->OnUserJoined(userID);
}

void ClientConnectionManager::OnUserLeftChannel(const ChannelID& channelID, const UserID& userID)
{
	mChannelMap[channelID]->OnUserLeft(userID);
}

void ClientConnectionManager::OnRecvdData(const ChannelID& channelID, const UserID& from, const byte* data, size_t length, bool wasReliable)
{
	mChannelMap[channelID]->OnDataReceived(from, data, length, wasReliable);
}

void ClientConnectionManager::OnRecvServerID(const UserID& userID)
{
	mServerID = userID;
}

void ClientConnectionManager::SendUnicastData(const ChannelID& channelID, const UserID& to, const byte* data, size_t length, bool isReliable)
{
	mUserManager->SendUnicastMsg(channelID, to, data, length, isReliable);
}

void ClientConnectionManager::SendMulticastData(const ChannelID& channelID, const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable)
{
	mUserManager->SendMulticastMsg(channelID, to, data, length, isReliable);
}

void ClientConnectionManager::SendBroadcastData(const ChannelID& channelID, const byte* data, size_t length, bool isReliable)
{
	mUserManager->SendBroadcastMsg(channelID, data, length, isReliable);
}

IDiscoveredGame* ClientConnectionManager::discoverGame(const std::wstring& gameName)
{
	IDiscoveredGame* pDiscoveredGame = NULL;

	std::vector<IDiscoveredGame*> games = mDiscoverer->GetGames();
	for (size_t i = 0; i < games.size(); ++i)
		if (games[i]->GetName() == gameName)
			pDiscoveredGame = games[i];
		else
			delete games[i];

	return pDiscoveredGame;
}

bool ClientConnectionManager::connect()
{
	Platform::DestroyUserManagerClient(mUserManager);
	mUserManager = Platform::CreateUserManagerClient(mUserManagerClassName);
	if (mUserManager == NULL)
		return false;

	mConnAttemptCounter++;
	mExiting = false;

	std::auto_ptr<IDiscoveredGame> game(discoverGame(mGameName));
	IDiscoveredUserManager* chosenUserManager = mPolicy->Choose(game.get(), mUserManagerClassName);

	return mUserManager->Connect(chosenUserManager, this);
}
