package org.bsd4us.utilities;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.FileVisitResult.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChokePointApp
{

	public static class ChokePointFinder extends SimpleFileVisitor< Path >
	{
		private final PathMatcher matcher;
		private ChokePointDB db;

		ChokePointFinder( String pattern, ChokePointDB db )
		{
			this.db = db;
			matcher = FileSystems.getDefault().getPathMatcher( "glob:" + pattern );
		}

		void find( Path file )
		{
			try
			{
				this.db.insert( file );
			} catch( Exception e )
			{
				System.out.println( "Exception:" + e.toString() );
			}
		}

		void done()
		{

		}

		@Override
		public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
		{
			find( file );
			return CONTINUE;
		}

		@Override
		public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs )
		{
			find( dir );
			return CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed( Path file, IOException exc )
		{
			System.err.println( exc );
			return CONTINUE;
		}
	}

	public ChokePointDB db;
	private Logger logger;

	public void run( String dbpath, String root ) throws Exception
	{
		this.logger = LogManager.getLogger( ChokePointApp.class );
		logger.info( "Starting with " + dbpath + " and " + root );
		this.db = new ChokePointDB( dbpath );

		// enter the root, start walking it
		Path startingDir = Paths.get( root );
		String pattern = "*";

		ChokePointFinder finder = new ChokePointFinder( pattern, db );
		Files.walkFileTree( startingDir, finder );
		finder.done();

		//now, grab anything from the DB that wasn't updated; this should be our deleted list
		this.db.listDeletes();
	}
}
