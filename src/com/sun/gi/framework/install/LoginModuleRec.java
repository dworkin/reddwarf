package com.sun.gi.framework.install;

import com.sun.gi.framework.install.xml.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LoginModuleRec {
  String classname;
  public LoginModuleRec() {
  }

  /**
   * LoginModuleRec
   *
   * @param lOGINMODULE LOGINMODULE
   */
  public LoginModuleRec(LOGINMODULE lOGINMODULE) {
    classname = lOGINMODULE.getClassname();
  }

  /**
   * getModuleClassName
   *
   * @return String
   */
  public String getModuleClassName() {
    return classname;
  }

}
