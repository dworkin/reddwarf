package com.sun.gi.framework.install;

import java.util.Map;

public interface ValidatorRec {

	/**
	 * getModuleClassName
	 *
	 * @return String
	 */
	public String getValidatorClassName();
	public String getParameter(String tag);
	/**
	 * @return
	 */
	public Map<String, String> getParameterMap(); 

}