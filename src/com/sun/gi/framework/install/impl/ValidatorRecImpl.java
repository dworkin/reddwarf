package com.sun.gi.framework.install.impl;

import java.util.HashMap;
import java.util.Map;

import com.sun.gi.framework.install.ValidatorRec;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

public class ValidatorRecImpl implements ValidatorRec {
	String classname;

	Map<String, String> params = new HashMap<String, String>();

	/**
	 * LoginModuleRec
	 * 
	 * @param lOGINMODULE
	 *            LOGINMODULE
	 */
	public ValidatorRecImpl(String loginModuleClassName) {
		classname = loginModuleClassName;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.install.LoginModuleRec#getModuleClassName()
	 */
	public String getValidatorClassName() {
		return classname;
	}

	public void setParameter(String tag, String value) {
		params.put(tag, value);
	}

	public String getParameter(String tag) {
		return params.get(tag);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.framework.install.ValidatorRec#getParameterMap()
	 */
	public Map<String, String> getParameterMap() {

		return params;
	}

}
