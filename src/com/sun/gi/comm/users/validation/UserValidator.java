package com.sun.gi.comm.validation;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import java.nio.ByteBuffer;

public interface UserValidator {
 public Callback[] nextDataRequest();
 public void dataResponse(Callback[] buff);
 public boolean authenticated();
}
