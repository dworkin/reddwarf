package com.installercore.step;

public class StandardOutStringReportCallback implements IStringReportCallback {
	
	int lastsize = -1;
	
	public void report(String s) {
		/*if(s != null)
		{
			for(int prints = 0; prints < lastsize; prints++)
			{
				System.out.print("\b");
			}
			System.out.print(s);
			lastsize = s.length();
		}*/
	}

}
