package com.sun.gi.comm.users.server;

import java.util.Map;

import com.sun.gi.comm.users.validation.UserValidatorFactory;

/**
 * Defines the primary class necessary to
 * implement the server side of an SGS UserManager.  A user manager
 * is responsible for creating a SGSUser for each legitimately logged in
 * user and reigstering it with the Router.  It is strongly recommended
 * that implementors use the provided SGSUserImpl class for their SGSUsers.
 * <p>
 * Copyright (c) 2004-2006 Sun Microsystems, TMI
 *
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface UserManager {

    /**
     * Sets the login validator factory
     *
     * @param factory the UserValidatorFactory for this UserManager
     */
    public void setUserValidatorFactory(UserValidatorFactory factory);

    /**
     * Provides the name of a class that a client should
     * instantiate in order to connect to this SGS
     * Server UserManager.  That class implements the
     * UserManagerClient interface.
     *
     * @return the fully-qualified name of the SGS UserManagerClient
     *         class correspoding to this server-side UserManager.
     */
    public String getClientClassname();

    /**
     * getClientParams
     *
     * @return A map of parmeters to be used by the client to initialize
     *         the UserManagerClient class when it connects to this
     *         UserManager.
     */
    public Map<String, String> getClientParams();
}
