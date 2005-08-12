package com.sun.gi.framework.logging;

public class SGSERRORCODES {
	public enum FatalErrors {
		RouterFailure;
		public void fail(String reason){
			//TODO
			System.err.println(reason);
			System.exit(0);
		}
	};
}
