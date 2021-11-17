package org.bsd4us.utilities;

public class ChokePoint {
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ChokePointApp app = new ChokePointApp();
		try
		{
			app.run( args[0], args[1] );			
		}
		catch( Exception e )
		{
			System.out.println( e.toString() );
		}
	}
}
