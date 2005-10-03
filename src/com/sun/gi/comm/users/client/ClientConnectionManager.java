package com.sun.gi.comm.users.client;

import com.sun.gi.comm.discovery.Discoverer;
import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.DiscoveredUserManager;
import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;

import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.validation.ValidationDataProtocol;

public interface ClientConnectionManager
   {
  
  public void setListener(ClientConnectionManagerListener l);

  public String[] getUserManagerClassNames();  

  public boolean connect(String userManagerClassName) throws 
          ClientAlreadyConnectedException;    

  public void disconnect();
   
  public void sendValidationResponse(Callback[] cbs);

  public void sendToServer(ByteBuffer buff,boolean reliable);
 
  public SGSChannel openChannel(String channelName);


}
