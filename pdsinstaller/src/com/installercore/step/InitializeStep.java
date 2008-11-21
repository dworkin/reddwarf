package com.installercore.step;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.installercore.metadata.MetadataDatabase;
import com.installercore.var.VarDatabase;

public class InitializeStep implements IStep {
	
	private String metadata;
	private String sysvars;
	
	public InitializeStep(String metadataLocation, String sysvarsLocation)
	{
		this.metadata = metadataLocation;
		this.sysvars = sysvarsLocation;
	}

	public void run() throws StepException {
		URL url;
		InputStream stream;
		try
		{
			url = this.getClass().getResource(sysvars);
			stream = url.openStream();
			VarDatabase.populate(stream);
		} catch (IOException e) {
			throw new StepException("Unable to access metadata resource file. This isn't your fault.");
		}
		try {
			url = this.getClass().getResource(metadata);
			stream = url.openStream();
			MetadataDatabase.populate(stream);
		} catch (IOException e) {
			throw new StepException("Unable to access metadata resource file. This isn't your fault.");
		}		
	}
}
