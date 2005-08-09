package com.sun.gi.comm.validation;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import javax.security.auth.*;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.*;

class SimplePrincipal implements Principal {
  String name;
  public SimplePrincipal(String name){
    this.name=name;
  }

  /**
   * hashCode
   *
   * @return int
   */
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * equals
   *
   * @param another Object
   * @return boolean
   */
  public boolean equals(Object another) {
    return name.equals(another);
  }

  /**
   * getName
   *
   * @return String
   */
  public String getName() {
    return name;
  }

  /**
   * toString
   *
   * @return String
   */
  public String toString() {
    return "SimplePrincipal: name="+name;
  }

}

public class FlatFileLoginModule
    implements LoginModule {
  Map passwdMap = new HashMap();
  private Subject currentSubject;
  private CallbackHandler currentCallbackHandler;
  private Callback[] cbs = new Callback[2];
  private List principles = new ArrayList();

  public FlatFileLoginModule(){
    URL passwdURL;
    String passwdFile = System.getProperty("flatfilelogin.passwordURL");
    if (passwdFile == null){
      try {
        passwdURL = new File("passwd.txt").toURL();
      }
      catch (MalformedURLException ex) {
        ex.printStackTrace();
        return;
      }
    } else {
      try {
        passwdURL = new URL(passwdFile);
      }
      catch (MalformedURLException ex1) {
        ex1.printStackTrace();
        return;
      }
    }
    setPasswdURL(passwdURL);
  }

  public FlatFileLoginModule(URL passwdURL) {
    setPasswdURL(passwdURL);
  }
  /**
   * setPasswdURL
   *
   * @param passwdURL URL
   */
  private void setPasswdURL(URL passwdURL) {
    try {
      BufferedReader rdr =
          new BufferedReader(new InputStreamReader(passwdURL.openStream()));
      String input = rdr.readLine();
      while (input != null) {
        StringTokenizer tok = new StringTokenizer(input);
        String name = tok.nextToken();
        String passwd = tok.nextToken();
        passwdMap.put(name, passwd);
        input = rdr.readLine();
      }
      rdr.close();
      cbs[0] = new NameCallback("Enter user name:");
      cbs[1] = new PasswordCallback("Enter user password:", false);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * abort
   *
   * @return boolean
   */
  public boolean abort() {
    principles.clear();
    return true;
  }

  /**
   * commit
   *
   * @return boolean
   */
  public boolean commit() {
  currentSubject.getPrincipals().addAll (principles);
    return true;
  }

    /**
     * login
     *
     * @return boolean
     */
    public boolean login() throws LoginException {
      try {
        currentCallbackHandler.handle(cbs);
        String name = ( (NameCallback) cbs[0]).getName();
        String password = new String(((PasswordCallback)cbs[1]).getPassword());
        if (((String)passwdMap.get(name)).equals(password)){
          principles.add(
              new SimplePrincipal(( (NameCallback) cbs[0]).getName()));
          return true;
        }
        else {
          return false;
        }
      }
      catch (UnsupportedCallbackException ex) {
        ex.printStackTrace();
        throw new LoginException(
            "Unsupported callback exception from cb handler.");
      }
      catch (IOException ex) {
        ex.printStackTrace();
        throw new LoginException("IO exception from cb handler.");
      }
    }

    /**
     * logout
     *
     * @return boolean
     */
    public boolean logout() {
      currentSubject.getPrincipals().removeAll(principles);
      return false;
    }

    /**
     * initialize
     *
     * @param subject Subject
     * @param callbackHandler CallbackHandler
     * @param sharedState Map
     * @param options Map
     */
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                           Map sharedState, Map options) {
      currentSubject = subject;
      currentCallbackHandler = callbackHandler;
      principles.clear();
      // no shared state or options necc for this LoginModule
    }
  }
