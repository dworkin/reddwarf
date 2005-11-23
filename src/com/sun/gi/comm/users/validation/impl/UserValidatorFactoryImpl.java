/**
 *
 * <p>Title: UserValidatorFactoryImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.comm.users.validation.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sun.gi.comm.users.validation.UserValidator;
import com.sun.gi.comm.users.validation.UserValidatorFactory;

/**
 * 
 * <p>
 * Title: UserValidatorFactoryImpl.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class UserValidatorFactoryImpl implements UserValidatorFactory {
	List<Constructor> userValidatorConstructors = new ArrayList<Constructor>();
	List<Map<String, String>> userValidatorParams = new ArrayList<Map<String,String>>();


	public UserValidatorFactoryImpl(){
		// nothing to do
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidatorFactory#newValidators()
	 */
	public UserValidator[] newValidators() {
		UserValidator[] validators = new UserValidator[userValidatorConstructors.size()];
		int i = 0;
		Iterator<Map<String,String>> iter = userValidatorParams.iterator();
		for(Constructor con : userValidatorConstructors){
			try {
				validators[i++]= (UserValidator)con.newInstance(iter.next());
			} catch (IllegalArgumentException e) {
				
				e.printStackTrace();
			} catch (InstantiationException e) {
				
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				
				e.printStackTrace();
			}
		}
		return validators;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidatorFactory#addLoginModule(java.lang.Class, java.util.Map)
	 */
	public void addLoginModule(Class loginModuleClass, Map<String, String> params) {
		try {
			Constructor c = loginModuleClass.getConstructor(Map.class);
			userValidatorConstructors.add(c);
			userValidatorParams.add(params);
		} catch (SecurityException e) {
			
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			
			e.printStackTrace();
		}
		
	}

}
