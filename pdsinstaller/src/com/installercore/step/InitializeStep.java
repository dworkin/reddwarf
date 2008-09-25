package com.installercore.step;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.installercore.metadata.MetadataDatabase;

public class InitializeStep implements IStep {
	
	private String resource;
	
	public InitializeStep(String metadataLocation)
	{
		this.resource = metadataLocation;
	}

	public void run() throws StepException {
		URL url = this.getClass().getResource(resource);
		InputStream stream;
		try {
			stream = url.openStream();
			MetadataDatabase.populate(stream);
		} catch (IOException e) {
			throw new StepException("Unable to access metadata resource file. This isn't your fault.");
		}
	}

}
