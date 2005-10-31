package com.sun.gi.comm.users.validation.impl;

import java.util.ArrayList;
import java.util.List;

import com.sun.gi.comm.users.validation.UserValidator;
import com.sun.gi.comm.users.validation.UserValidatorFactory;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LoginModuleValidatorFactory implements UserValidatorFactory {
  List<Class> classes = new ArrayList<Class>();
  
  public LoginModuleValidatorFactory() {
  }
  
  public UserValidator newValidator() {
	 Class[] ca = new Class[classes.size()];
	 return new UserValidatorImpl(classes.toArray(ca)); 
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
