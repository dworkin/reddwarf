package com.sun.gi.comm.users.validation;

import java.util.Map;

public interface UserValidatorFactory {

    public UserValidator[] newValidators();

    public void addLoginModule(Class loginModuleClass, Map<String, String> params);
}
