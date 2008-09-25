package com.installercore.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dsinstaller.DSInstallerStrings;

/**
 * {@link MetadataDatabase} contains all metadata files that are available
 * to the system.
 * @author Paul Gibler
 * @see IMetadata
 *
 */
public class MetadataDatabase {
	
	enum MetadataTypes
	{
		ExternalFile ("EXTERNAL", ExternalMetadata.class),
		EmbeddedFolder ("EMBEDDED", EmbeddedMetadata.class);
		
		private Class<?> type;
		private String header;
		
		MetadataTypes(String header, Class<?> type)
		{
			this.type = type;
			this.header = header;
		}
		
		public Class<?> getType()
		{
			return this.type;
		}
		
		public String toString()
		{
			return this.header;
		}
	}
	
	/**
	 * Contains all of the global {@link IMetadata}.
	 */
	private static Map<String, IMetadata> metadata = new HashMap<String, IMetadata>();
	
	public static String InstallLocation = "";
	
	private MetadataDatabase() {}
	
	/**
	 * Adds an {@link IMetadata} object to the database, if it does not exist.
	 * If the name already exists in the database, then the related {@link IMetadata}
	 * is overwritten. 
	 * @param name The name of the metadata to be stored.
	 * @param value The value that the key will be set to.
	 */
	public static void setValue(String member, IMetadata value)
	{
		metadata.put(member, value);
	}
	
	/**
	 * Obtains the {@link IMetadata} that is associated with the input key value.
	 * @param member The name of the metadata to be retrieved.
	 * @return The associated {@link IMetadata} object if it is set, otherwise this returns null.
	 */
	public static IMetadata getValue(String member)
	{
		return metadata.get(member);
	}
	
	/**
	 * Returns a {@link Set} of all of the {@link IMetadata} in proper order.
	 * @return A {@link Set} containing the names of each piece of {@link IMetadata} in the order
	 * in which they are to be executed. 
	 */
	public static Set<String> getMetadataNames()
	{
		return metadata.keySet();
	}
	
	/**
	 * Returns the number of {@link IMetadata} objects stored in the database.
	 * @return The number of {@link IMetadata} objects stored in the database.
	 */
	public static int count()
	{
		return metadata.size();
	}
	
	/**
	 * Generates a {@link List} of {@link IMetadata} objects by reading through
	 * a file and parsing the fields for each piece of metadata.
	 * @param metadataInput An {@link InputStream} from which the metadata will be read.
	 * @throws IOException If an I/O error occurs while attempting to access the metadata file.
	 */
	public static void populate(InputStream metadataInput) throws IOException
	{
		List<IMetadata> lulwut = new LinkedList<IMetadata>();
		
		BufferedReader br = new BufferedReader(new InputStreamReader(metadataInput));
		
		String line = "";
		String header = null;
		String name = null;
		String source = null;
		String file = null;
		String destination = null;
		
		while((line = br.readLine()) != null)
		{
			if(line.trim().equals(""))
			{
				continue;
			}
			
			if(isHeader(line))
			{
				if(header != null)
				{
					addMetadata(lulwut, header, name, source, file, destination);
				}
				header = getHeader(line);
				name = null;
				source = null;
				file = null;
				destination = null;
			}
			else
			{
				String[] parts = line.trim().split(":");
				if(isName(line))
				{
					name = parts[1];
				}
				else if(isSource(line))
				{
					source = parts[1];
					while(source.startsWith(DSInstallerStrings.seperator))
					{
						source = source.substring(1, source.length());
					}
				}
				else if(isFile(line))
				{
					file = parts[1];
				}
				else if(isDestination(line))
				{
					destination = parts[1];
				}
			}
		}
		
		addMetadata(lulwut, header, name, source, file, destination);
		
		for(IMetadata mdata : lulwut)
		{
			setValue(mdata.getName(), mdata);
		}
	}
	
	private static void addMetadata(List<IMetadata> lulwut, String header, String name, String source, String file, String destination)
	{
		if(header.equals(MetadataTypes.ExternalFile.toString()))
		{
			lulwut.add(new ExternalMetadata(name, source, file, destination));
		}
		else if(header.equals(MetadataTypes.EmbeddedFolder.toString()))
		{
			lulwut.add(new EmbeddedMetadata(name, source, file, destination));
		}
	}
	
	
	private static boolean isName(String line) {
		return line.trim().startsWith("name");
	}
	
	private static boolean isSource(String line) {
		return line.trim().startsWith("source");
	}
	
	private static boolean isFile(String line) {
		return line.trim().startsWith("file");
	}
	
	private static boolean isDestination(String line) {
		return line.trim().startsWith("destination");
	}

	private static boolean isHeader(String line) {
		return line.startsWith("\t");
	}
	
	private static String getHeader(String line) {
		return line.substring(1, line.length());
	}
}
