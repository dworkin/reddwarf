#include "stable.h"

#include "ClientChannel.h"

#include "IClientChannelListener.h"

#include "ClientConnectionManager.h"

ClientChannel::ClientChannel(ClientConnectionManagerPtr manager, const std::wstring& channelName, const ChannelID& channelID) :
	mID(channelID),
	mName(channelName),
	mManager(manager)
{
}

std::wstring ClientChannel::GetName() const
{
	return mName;
}

void ClientChannel::SetListener(IClientChannelListenerPtr listener)
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

void ClientChannel::OnChannelClosed()
{
	if (mListener)
		mListener->OnChannelClosed();
}

void ClientChannel::OnUserJoined(const UserID& userID)
{
	if (mListener)
		mListener->OnPlayerJoined(userID);
}

void ClientChannel::OnUserLeft(const UserID& userID)
{
	if (mListener)
		mListener->OnPlayerLeft(userID);
}

void ClientChannel::OnDataReceived(const UserID& from, const byte* data, size_t length, bool wasReliable)
{
	if (mListener)
		mListener->OnDataArrived(from, data, length, wasReliable);
}
