package com.sun.gi.comm.users.validation;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;

public interface UserValidator {
 public Callback[] nextDataRequest();
 public void dataResponse(Callback[] buff);
 public boolean authenticated();
 public void reset(Subject subject);

}
