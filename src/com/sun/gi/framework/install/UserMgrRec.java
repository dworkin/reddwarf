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
	public List<LoginModuleRec> getLoginModules();

	/**
	 * hasLoginModules
	 *
	 * @return boolean
	 */
	public boolean hasLoginModules();

	public Object getParameterMap();

}