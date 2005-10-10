package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;



public interface ClientConnectionManager
   {
  
  public void setListener(ClientConnectionManagerListener l);

  public String[] getUserManagerClassNames();  

  public boolean connect(String userManagerClassName) throws 
          ClientAlreadyConnectedException;    

  public void disconnect();
   
  public void sendValidationResponse(Callback[] cbs);

  public void sendToServer(ByteBuffer buff,boolean reliable);
 
  public ClientChannel openChannel(String channelName);


}
