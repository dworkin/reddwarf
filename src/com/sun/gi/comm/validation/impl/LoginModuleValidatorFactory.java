package com.sun.gi.comm.validation.impl;

import com.sun.gi.comm.validation.*;
import java.util.List;
import java.util.ArrayList;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LoginModuleValidatorFactory implements UserValidatorFactory {
  List classes = new ArrayList();
  public LoginModuleValidatorFactory() {
  }
  public UserValidator newValidator() {
    /**@todo Implement this com.sun.gi.comm.validation.UserValidatorFactory method*/
    throw new java.lang.UnsupportedOperationException("Method newValidator() not yet implemented.");
  }

  /**
   * addLoginModule
   *
   * @param loginModuleClass Class
   */
  public void addLoginModule(Class loginModuleClass) {
    classes.add(loginModuleClass);
  }

}
