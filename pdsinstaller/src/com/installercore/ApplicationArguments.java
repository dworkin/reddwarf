package com.installercore;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * {@link ApplicationArguments} contains a data store of all of the arguments passed into the application. 
 * @author Paul Gibler
 *
 */
public class ApplicationArguments {
	private Map<String, List<String>> arguments = new HashMap<String, List<String>>();
	
	private ApplicationArguments() {}
	
	/**
	 * Returns an argument value. If the argument has no value, this function returns null.
	 * @param arg The argument whose value is being sought.
	 * @return The value of the argument as a {@link String}.
	 */
	public String getArgumentValue(String arg)
	{
		String returnme = "";
		List<String> list = arguments.get(arg);
		for(String curr : list)
		{
			returnme = returnme.concat(curr+" ");
		}
		returnme = returnme.trim();
		return returnme;
	}
	
	public List<String> getArgumentValueAsList(String arg)
	{
		return arguments.get(arg);
	}
	
	/**
	 * Determines if the program has a certain argument passed to it.
	 * @param arg The argument whose existence is being determined.
	 * @return True if the argument has been passed to the program, otherwise false.
	 */
	public boolean hasArgument(String arg)
	{
		return arguments.keySet().contains(arg);
	}
	
	/**
	 * Parses an {@link Array} containing all of the command-line arguments.
	 * @param args The arguments from the command-line.
	 * @return An instance of Argument containing the parsed command-line arguments.
	 * @throws Exception If the command line arguments cannot be parsed.
	 */
	public static ApplicationArguments parseCommandLine(String args[]) throws Exception
	{
		ApplicationArguments returnme = new ApplicationArguments();
		String currArg = "";
		String curr = "";
		
		for(int i=0; i < args.length; i++)
		{
			curr = args[i];
			if(curr.charAt(0) == '-')
			{
				currArg = args[i].substring(1,curr.length());
				if(!returnme.arguments.containsKey(currArg))
				{
					returnme.arguments.put(currArg, new LinkedList<String>());
				}
				else
				{
					throw new Exception("Duplicate argument found.");
				}
			}
			else
			{
				returnme.arguments.get(currArg).add(curr);
			}
		}
		
		return returnme;
	}
}
