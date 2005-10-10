package com.sun.gi.comm.users.server;

import java.nio.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.comm.users.validation.UserValidatorFactory;



/**
 *
 * <p>Title: UserManager</p>
 * <p>Description: This interface defines the primary class necessary to implement the server side of a 
 * Darkstar UserManager.  A user manager is responsible for creating a SGSUser for each legitimately logged in
 * user and reigstering it with the Router.  It is strongly recommended that implementors use the provided 
 * SGSUserImpl class for their SGSUsers. 
 * </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface UserManager  {
    
 
  /***
   *  sets the login validator
   *
   * @param UserValifator validator
   */
  public void setUserValidatorFactory(UserValidatorFactory validatorFactory);

  

  /**
   * getClientClassname
   *
   * @return String The FQCN of the client class thatr knows how to connect and
   * talk to this user manager.
   */
  public String getClientClassname();

  /**
   * getClientParams
   *
   * @return Map A map of parmeters to be used by the client to initialize the
   * user manager client class.
   */
  public Map getClientParams();

}
