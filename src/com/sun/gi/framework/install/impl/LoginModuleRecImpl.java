package com.sun.gi.framework.install.impl;

import com.sun.gi.framework.install.LoginModuleRec;



/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LoginModuleRecImpl implements LoginModuleRec {
  String classname;
  
  /**
   * LoginModuleRec
   *
   * @param lOGINMODULE LOGINMODULE
   */
  public LoginModuleRecImpl(String loginModuleClassName) {
	  classname = loginModuleClassName;
   
  }

  /* (non-Javadoc)
 * @see com.sun.gi.framework.install.LoginModuleRec#getModuleClassName()
 */
  public String getModuleClassName() {
    return classname;
  }

}
