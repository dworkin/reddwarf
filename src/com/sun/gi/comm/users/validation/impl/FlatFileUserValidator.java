/**
 *
 * <p>Title: FlatFileUserValidator.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.comm.users.validation.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.Principal;
import java.util.Map;
import java.util.StringTokenizer;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import com.sun.gi.comm.users.validation.UserValidator;
import com.sun.gi.framework.install.DeploymentRec;

/**
 *
 * <p>Title: FlatFileUserValidator.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class FlatFileUserValidator implements UserValidator {
	private Subject subject = null;
	private Map<String,String> params;
	private boolean authenticated = false;
	
	public FlatFileUserValidator(Map<String,String> parameters){
		params = parameters;
	}
	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidator#nextDataRequest()
	 */
	public Callback[] nextDataRequest() {
		if (subject == null) {
			subject = new Subject();
			return new Callback[] {new NameCallback("User Name","Guest"),
				new PasswordCallback("Password",false)};
		} else {
			return null;
		}
	
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidator#dataResponse(javax.security.auth.callback.Callback[])
	 */
	public void dataResponse(Callback[] buff) {
		String name="";
		String password="";
		for(Callback cb : buff){
			if (cb instanceof NameCallback){
				name = ((NameCallback)cb).getName();
			} else if (cb instanceof PasswordCallback) {
				password = new String(((PasswordCallback)cb).getPassword());
			}
		}
		try {
			URL passfileURL = new URL(params.get("password_file_url"));
			URLConnection conn = passfileURL.openConnection();
			conn.connect();
			InputStream content = conn.getInputStream();
			InputStreamReader isrdr = new InputStreamReader(content);
			BufferedReader rdr = new BufferedReader(isrdr);
			String inline= rdr.readLine();
			while (inline!=null){
				StringTokenizer tok = new StringTokenizer(inline);
				String usrname = tok.nextToken();
				if (name.charAt(0)!='#'){ // skip comments
					String usrpass = tok.nextToken();
					if (usrname.equals(name) && 
							(usrpass.equals("*")||usrpass.equals(password))){ // found matchign entry
						authenticated=true;
						subject.getPrincipals().add(new StringPrincipal(usrname));						
						while (tok.hasMoreTokens()){
							String credential = tok.nextToken();
							subject.getPublicCredentials().add(credential);
						}
					}
				}
				inline = rdr.readLine();
			}
			
		} catch (MalformedURLException e) {			
			e.printStackTrace();
		} catch (IOException e) {			
			e.printStackTrace();
		}
		

	}

	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidator#authenticated()
	 */
	public boolean authenticated() {		
		return authenticated;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidator#getSubject()
	 */
	public Subject getSubject() {		
		return subject;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.comm.users.validation.UserValidator#reset()
	 */
	public void reset(Subject subject) {
		authenticated = false;
		this.subject = subject;
	}
	
	class StringPrincipal implements Principal {
		String name;
		
		public StringPrincipal(String str){
			name = str;
		}
		/* *
		 * Returns the name of this principal
		 * @return the principal name
		 * @see java.security.Principal#getName()
		 */
		public String getName() {			
			return name;
		}
		
		public boolean equals(Object obj){			
			return name.equals(obj);
		}
		
		public int hashCode(){
			return name.hashCode();
		}
		
		public String toString(){
			return "StringPrincipal:"+name;
		}
	}
	

}
