package com.sun.gi.comm.validation.impl;

import java.nio.*;
import javax.security.auth.*;
import javax.security.auth.callback.*;
import javax.security.auth.spi.*;

import com.sun.gi.comm.validation.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */


public class UserValidatorImpl implements UserValidator {
  LoginHandler loginHandler;
  int currentLM;
  Subject subject;
  boolean authenticated;
  Thread loginThread;


  /**
   * UserValidatorImpl
   *
   * @param classes Class[]
   */
  public UserValidatorImpl(Class[] loginModuleClasses) {
    subject = new Subject();
    LoginModule[] loginModules = new LoginModule[loginModuleClasses.length];
    for(int i=0;i<loginModuleClasses.length;i++){
      if (loginModuleClasses[i] != null) {
        try {
          loginModules[i] = (LoginModule) loginModuleClasses[i].newInstance();
        }
        catch (Exception ex) {
          System.out.println(
              "Exception creating a login module.  Module ignored.");
          ex.printStackTrace();
          loginModules[i] = null;
        }

      } else {
        loginModules[i] = null;
      }
    }
    setupLoginThread(subject,loginModules);
  }

  /**
   * setupLoginThread
   */
  private void setupLoginThread(Subject subject,LoginModule[] loginModules) {
    loginHandler = new LoginHandler(subject,loginModules);
    new Thread(loginHandler).start();
  }


  /**
   * authenticated
   *
   * @return boolean
   */
  public boolean authenticated() {
    return loginHandler.authenticated();
  }

  /**
   * dataResponse
   *
   * @param buff ByteBuffer
   */
  public void dataResponse(Callback[] buff) {
    try {
      loginHandler.dataResponse(buff);
    }
    catch (UnsupportedCallbackException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * needsData
   *
   * @return boolean
   */
  public Callback[] nextDataRequest() {
    return loginHandler.nextDataRequest();

  }

}
