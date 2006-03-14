#include "stable.h"

#include "ClientChannel.h"

#include "IClientChannelListener.h"

#include "ClientConnectionManager.h"

using namespace SGS;
using namespace SGS::Internal;

ClientChannel::ClientChannel(ClientConnectionManager* manager, const std::wstring& channelName, const ChannelID& channelID) :
	mID(channelID),
	mName(channelName),
	mManager(manager),
	mListener(NULL)
{
}

std::wstring ClientChannel::GetName() const
{
	return mName;
}

void ClientChannel::SetListener(IClientChannelListener* listener)
{
	mListener = listener;
}

void ClientChannel::SendUnicastData(const UserID& to, const byte* data, size_t length, bool isReliable)
{
	mManager->SendUnicastData(mID, to, data, length, isReliable);
}

void ClientChannel::SendMulticastData(const std::vector<UserID>& to, const byte* data, size_t length, bool isReliable)
{
	mManager->SendMulticastData(mID, to, data, length, isReliable);
	
}

void ClientChannel::SendBroadcastData(const byte* data, size_t length, bool isReliable)
{
	mManager->SendBroadcastData(mID, data, length, isReliable);
}

void ClientChannel::Close()
{
	mManager->CloseChannel(mID);
}

void ClientChannel::OnChannelClosed()
{
	if (mListener)
		mListener->OnChannelClosed(this);
}

void ClientChannel::OnUserJoined(const UserID& userID)
{
	if (mListener)
		mListener->OnPlayerJoined(this, userID);
}

void ClientChannel::OnUserLeft(const UserID& userID)
{
	if (mListener)
		mListener->OnPlayerLeft(this, userID);
}

void ClientChannel::OnDataReceived(const UserID& from, const byte* data, size_t length, bool wasReliable)
{
	if (mListener)
		mListener->OnDataArrived(this, from, data, length, wasReliable);
}
