package com.sun.gi.comm.users.server;


import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.utils.ReversableMap;
import com.sun.gi.utils.nio.NIOSocketManager;
import com.sun.gi.utils.nio.NIOSocketManagerListener;
import com.sun.gi.utils.nio.NIOTCPConnection;

public class TCPIPUserManager
    implements NIOSocketManagerListener, UserManager, RouterListener 
   {
  Router router;
  long gameID;
  UserValidatorFactory validatorFactory;  
  private String host="localhost";
  private int port=1139;
  private NIOSocketManager socketMgr;


  public TCPIPUserManager(Router router,Map params) throws
      InstantiationException {
    this.router = router;
    String p = (String)params.get("host");
    if (p!=null) {
      host =p;
    }
    p = (String)params.get("port");
    if (p!=null){
      port = Integer.parseInt(p);
    }
    init();
  }

  private void init () throws
      InstantiationException {
    router.addRouterListener(this);
    System.out.println("Starting TCPIP User Manager on host " + host + " port " + port );
    try {
      socketMgr = new NIOSocketManager();
      socketMgr.addListener(this);
      socketMgr.acceptTCPConnectionsOn(host,port);
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new InstantiationException("TCPIPUserManager failed to initialize");
    }

  }


  /**
	 * getClientClassname
	 * 
	 * @return String
	 */
  public String getClientClassname() {
    return "com.sun.gi.comm.users.client.TCPIPUserManagerClient";
  }

  /**
	 * getClientParams
	 * 
	 * @return Map
	 */
  public Map getClientParams() {
    Map<String,String> params = new HashMap<String,String>();
    params.put("host",host);
    params.put("port",Integer.toString(port));
    return params;
  }

public void newTCPConnection(NIOTCPConnection connection) {
	TCPIPTransport conn = new TCPIPTransport(connection);
	
}

public void connected(NIOTCPConnection connection) {
	// TODO Auto-generated method stub
	
}

public void connectionFailed(NIOTCPConnection connection) {
	// TODO Auto-generated method stub
	
}

public void sendDataToUser(ChannelID cid, UserID id, UserID from, ByteBuffer buff, boolean reliable) {
	// TODO Auto-generated method stub
	
}

public void dataArrived(UserID to, UserID from, ByteBuffer data, boolean realiable) {
	// TODO Auto-generated method stub
	
}

public void userDropped(UserID userID) {
	// TODO Auto-generated method stub
	
}

public void userAdded(UserID userID) {
	// TODO Auto-generated method stub
	
}

public void newUserKey(UserID userID, byte[] key) {
	// TODO Auto-generated method stub
	
}

public void broadcastDataArrived(UserID from, ByteBuffer buff, boolean reliable) {
	// TODO Auto-generated method stub
	
}

 

}
