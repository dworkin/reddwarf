package com.sun.gi.comm.users;

import java.nio.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.comm.validation.*;

/**
 *
 * <p>Title: UserManager</p>
 * <p>Description: This interface defines the primary class necessary to
 * implement to create a user manager. </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public interface UserManager extends RouterListener {
    
 
  /***
   *  sets the login validator
   *
   * @param UserValifator validator
   */
  public void setUserValidatorFactory(UserValidatorFactory validatorFactory);

  /**
   * called by the router to transfer data to the user.
   * @param id UserID User to send data to
   * @param buff byte[] data to send to user.
   * @param length int length of data
   * @param reliable boolean whether or not reliable delivery is necessary
   */
  public void sendDataToUser(UserID id,UserID from, ByteBuffer buff,boolean reliable);

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
