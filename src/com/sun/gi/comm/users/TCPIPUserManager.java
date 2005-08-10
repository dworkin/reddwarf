package com.sun.gi.comm.users;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.utils.nio.*;
import com.sun.gi.comm.validation.UserValidator;
import com.sun.gi.comm.validation.UserValidatorFactory;
import com.sun.gi.utils.ReversableMap;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.transport.TransportListener;
import com.sun.gi.comm.transport.impl.TCPTransport;
import javax.security.auth.callback.Callback;
import com.sun.gi.comm.transport.Transport;
import sun.security.validator.Validator;
import javax.security.auth.callback.*;
import java.util.Map.Entry;

public class TCPIPUserManager
    implements UserManager, RouterListener, NIOSocketManagerListener,
   {
  Router router;
  long gameID;
  ReversableMap idToConnectionMap = new ReversableMap();

  UserValidatorFactory validatorFactory;
  private ReversableMap idToTransport = new ReversableMap();
  private Map validatorMap = new HashMap();
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
    Map params = new HashMap();
    params.put("host",host);
    params.put("port",Integer.toString(port));
    return params;
  }

  

}
