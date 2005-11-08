package com.sun.gi.framework.install;

import java.util.List;

public interface UserMgrRec {

	/**
	 * getServerClassName
	 *
	 * @return String
	 */
	public String getServerClassName();

	/**
	 * getParameterMap
	 *
	 * @return Object
	 */
	public String getParameter(String tag);

	/**
	 * listLoginModules
	 *
	 * @return Iterator
	 */
	public List<ValidatorRec> getValidatorModules();

	/**
	 * hasLoginModules
	 *
	 * @return boolean
	 */
	public boolean hasValidatorModules();

	public Object getParameterMap();

}