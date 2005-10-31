package com.sun.gi.framework.install.impl;

import java.util.*;

import com.sun.gi.framework.install.LoginModuleRec;
import com.sun.gi.framework.install.UserMgrRec;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class UserMgrRecImpl implements UserMgrRec {
  String serverclass;
  Map<String,String> parameters = new HashMap<String,String>();
  List<LoginModuleRec> loginModules = new ArrayList<LoginModuleRec>();
 
  /**
   * UserMgrRec
   *
   * @param uSERMANAGER USERMANAGER
   */
  public UserMgrRecImpl(String serverClassName) {
	  serverclass = serverClassName;
  }

  /* (non-Javadoc)
 * @see com.sun.gi.framework.install.UserMgrRec#getServerClassName()
 */
  public String getServerClassName() {
    return serverclass;
  }

  public void addParameter(String tag, String value){
	  parameters.put(tag,value);
  }
  /* (non-Javadoc)
 * @see com.sun.gi.framework.install.UserMgrRec#getParameterMap()
 */
  public String getParameter(String tag) {
    return parameters.get(tag);
  }

  public void addLoginModule(LoginModuleRec lmrec){
	  loginModules.add(lmrec);
  }
  /* (non-Javadoc)
 * @see com.sun.gi.framework.install.UserMgrRec#listLoginModules()
 */
  public List<LoginModuleRec> getLoginModules() {
    return loginModules;
  }

  /* (non-Javadoc)
 * @see com.sun.gi.framework.install.UserMgrRec#hasLoginModules()
 */
  public boolean hasLoginModules() {
    return loginModules.size()>0;
  }

public Map<String,String> getParameterMap() {
	return parameters;
}



}
