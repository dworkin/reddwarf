package com.sun.sgs.tutorial.server.swordworld;

import java.io.Serializable;

import com.sun.sgs.app.ManagedObject;

public class SwordWorldObject implements ManagedObject, Serializable {
	String description;
	public SwordWorldObject(String description) {
		this.description = description;
	}
	public String getDescription() {
		return description;
	}

}
