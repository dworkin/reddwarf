package com.sun.gi.comm.routing.old;

public interface CommunicationsManager {
  public void startHeartbeat();
  public String getAppName();
  public void sendDataToServer(UserID from, byte[] data);
  public void sendDataToUser(UserID from, UserID to, byte[] data);
  public void sendDataToChannel(UserID from, ChannelID id, byte[] data);
  public ChannelID createChannel(UserID user, String channelName, byte[] channelData);
  public ChannelID joinChannel(UserID user, String channelName);
  public void leaveChannel(UserID uid, ChannelID cid);
  public void deleteChannel(ChannelID id);
  public void setChannelData(ChannelID id, byte[] data);
  public byte[] getChannelData(ChannelID id);
  public UserID createUser(byte[] userData);
  public void deleteUser(UserID id);
  public byte[] getUserData(UserID id);
  public void setUserData(UserID id, byte[] data);
  // callbacks
  public void addChannelListener(ChannelListener listener);
  public void addUserListener(UserListener listener);
  public void addServerListener(ServerListener listener);

  public String getChannelName(ChannelID id);
  public ChannelID getChannelID(String name);
}
