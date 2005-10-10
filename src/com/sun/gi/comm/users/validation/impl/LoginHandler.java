package com.sun.gi.comm.validation.impl;

import java.nio.*;
import javax.security.auth.*;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LoginHandler
    implements Runnable, CallbackHandler {
  int currentLM = 0;
  LoginModule[] modules;
  Subject subject;
  boolean requestDataAvailable = false;
  private Callback[] currentCallbacks;
  private boolean authenticated = false;
  private ByteBuffer callerData;
  private Object callerDataSemaphore = new Object();
  private boolean requestReadyFlag = false;
  private Object dataRequestSemaphore = new Object();

  public LoginHandler(Subject subject, LoginModule[] modules) {
    this.modules = modules;
    this.subject = subject;
  }

  /**
   * run
   */
  public void run() {
    while (currentLM < modules.length) {
      if (modules[currentLM] == null) {
        // skip
        currentLM++;
      }
      else {
        modules[currentLM].initialize(subject, this, null, null);
        try {
          currentCallbacks = null;
          boolean result =modules[currentLM].login();
          if (!result) { // failed,set false and exit
            authenticated = false;
            currentCallbacks = null; // end signal
            requestReady();
            return; // exit
          }
          else {
            currentLM++;
          }
        }
        catch (LoginException ex) {
          ex.printStackTrace();
        }
      }
      synchronized (this) {
        this.notify(); // login module counter changed
      }
    }
    authenticated = true;
    currentCallbacks = null; // end signal
    requestReady();
  }

  /**
   * handle
   *
   * @param callbacks Callback[]
   */
  public void handle(Callback[] callbacks) throws UnsupportedCallbackException { // login needs mroe info
    currentCallbacks = callbacks;
    callerData = null;
    requestReady(); // request for data ready
    waitForCallerData();
  }

  /**
   * requestReady
   *
   * @param b boolean
   */
  private void requestReady() {
    requestReadyFlag = true;
    synchronized(dataRequestSemaphore){
      dataRequestSemaphore.notify();
    }
  }

  /**
   *
   * @return ByteBuffer
   */

  public Callback[] nextDataRequest() {
   while (!requestReadyFlag) {
     synchronized (dataRequestSemaphore) {
       try {
         dataRequestSemaphore.wait();
       }
       catch (InterruptedException ex) {
         ex.printStackTrace();
       }
     }
   }
   requestReadyFlag = false;
   return currentCallbacks;
 }


  /**
   * waitForCallerData
   */
  private void waitForCallerData() {
    while(callerData==null) {
      synchronized(callerDataSemaphore){
        try {
          callerDataSemaphore.wait();
        }
        catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  /**
  * dataResponse
  *
  * @param buff ByteBuffer
  */
 public void dataResponse(Callback[] newcbs) throws
      UnsupportedCallbackException {
   synchronized (callerDataSemaphore) {
     for (int i = 0; i < currentCallbacks.length; i++) {
       Callback cb = currentCallbacks[i];
       Callback newcb = newcbs[i];
       if (cb instanceof NameCallback) {
         ( (NameCallback) cb).setName( ( (NameCallback) newcb).getName());
       }
       else if (cb instanceof PasswordCallback) {
         ( (PasswordCallback) cb).setPassword( ( (PasswordCallback) newcb).
                                              getPassword());
       }
       else if (cb instanceof TextInputCallback) {
         ( (TextInputCallback) cb).setText( ( (TextInputCallback) newcb).
                                           getText());
       }
       else {
         throw new UnsupportedCallbackException(cb);
       }
     }
     callerDataSemaphore.notify();
   }
 }



  /**
   * authenticated
   */
  public boolean authenticated() {
    return authenticated;
  }

}
