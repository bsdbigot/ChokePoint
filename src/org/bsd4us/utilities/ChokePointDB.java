package org.bsd4us.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings( "unused" )
public class ChokePointDB
{
	private String dbpath;
	private Connection conn;
	private Logger logger;
	private Boolean isWindows;
	private Timestamp cpstartdate;

	public ChokePointDB()
	{
		this.dbpath = null;
	}

	public void setDBPath( String dbpath )
	{
		this.dbpath = dbpath;
	}

	public void listDeletes()
	{
		try
		{
			String sql = "select * from fileinfo where cpdate < ?";
			PreparedStatement pstmt = conn.prepareStatement( sql );
			pstmt.setTimestamp( 1, this.cpstartdate );
			ResultSet res = pstmt.executeQuery();
			DBRecord indb = null;

			while( res.next() )
			{
				this.logger.error( "MISSING: " + res.getString( "fullpath" ) );
				Path p = Paths.get( res.getString( "fullpath" ) );
				indb = new DBRecord( p, isWindows, true, conn );
				indb.delete( conn );
			}
		} catch( Exception e )
		{
		}

	}

	public void insert( Path path ) throws Exception
	{
		DBRecord ondisk = new DBRecord( path, isWindows, false, null );
		DBRecord indb = null;

		try
		{
			indb = new DBRecord( path, isWindows, true, conn );
			if( indb.ignore )
			{
				return;
			}
		} catch( RecordNotFoundException rnfe )
		{
			try
			{
				logger.warn( "New file: " + path );
				ondisk.insert( conn );
				return;
			} catch( Exception e )
			{
				throw( e );
			}
		}

		/* now, check for equality */
		if( !ondisk.equals( indb ) )
		{
			logger.error( "Mismatch: " + path );
			ondisk.update( conn );
			return;
		}

		indb.update( conn );
		logger.debug( "Matched: " + path );
	}

	public ChokePointDB( String dbpath ) throws Exception
	{
		if( null == dbpath )
		{
			throw new Exception( "DBPath not set" );
		}
		try
		{
			this.dbpath = dbpath;
			this.init();
		} catch( Exception e )
		{
			throw( e );
		}
	}

	public void init() throws Exception
	{
		Class.forName( "org.h2.Driver" );
		this.logger = LogManager.getLogger( ChokePointDB.class );
		this.conn = DriverManager.getConnection( "jdbc:h2:" + this.dbpath );
		String sql = "create table if not exists fileinfo ";
		String delsql = "create table if not exists deletedinfo ";

		this.cpstartdate = new Timestamp( System.currentTimeMillis() );

		this.isWindows = false;
		if( System.getProperty( "os.name" ).startsWith( "Windows" ) )
		{
			this.isWindows = true;
			sql += "( fullpath varchar(max), filetype char(2), permissions varchar(50), filesize long, modified datetime, created datetime, ignore boolean, filehash varchar(255), cpdate datetime )";
			delsql += "( fullpath varchar(max), filetype char(2), permissions varchar(50), filesize long, modified datetime, created datetime, ignore boolean, filehash varchar(255), cpdate datetime )";
		} else
		{
			sql += "( fullpath varchar(max), filetype char(2), permissions varchar(50), filesize long, modified datetime, created datetime, ignore boolean, filehash varchar(255), cpdate datetime )";
			delsql += "( fullpath varchar(max), filetype char(2), permissions varchar(50), filesize long, modified datetime, created datetime, ignore boolean, filehash varchar(255), cpdate datetime )";
		}
		logger.debug( "About to execute SQL: " + sql );
		Statement stmt = conn.createStatement();
		stmt.executeUpdate( sql );
		stmt.executeUpdate( delsql );

	}

	public static class DBRecord
	{
		public String fullpath;
		public String filetype;
		public String permissions;
		public Long filesize;
		public Timestamp modified;
		public Timestamp created;
		public Boolean ignore;
		public String filehash;
		public Timestamp cpdate;

		DBRecord( Path path, Boolean isWindows, Boolean fromdb, Connection conn ) throws Exception
		{
			String abspathuri = path.toAbsolutePath().toUri().toString();

			if( fromdb )
			{
				String selectsql = "select * from fileinfo where fullpath = ?";
				PreparedStatement pstmt = conn.prepareStatement( selectsql );
				pstmt.setString( 1, abspathuri );
				ResultSet res = pstmt.executeQuery();

				if( res.next() )
				{
					this.fullpath = res.getString( "fullpath" );
					this.filetype = res.getString( "filetype" );
					this.permissions = res.getString( "permissions" );
					this.filesize = res.getLong( "filesize" );
					this.modified = res.getTimestamp( "modified" );
					this.created = res.getTimestamp( "created" );
					this.ignore = res.getBoolean( "ignore" );
					this.filehash = res.getString( "filehash" );
				} else
				{
					throw( new RecordNotFoundException() );
				}
			} else
			{
				this.fullpath = abspathuri;

				if( isWindows )
				{
					DosFileAttributeView dfv = Files.getFileAttributeView( path, DosFileAttributeView.class );
					/** dos RHSA **/
				} else
				{
					PosixFileAttributeView pfv = Files.getFileAttributeView( path, PosixFileAttributeView.class );
					PosixFileAttributes atts = pfv.readAttributes();

					this.filetype = "O";
					if( atts.isDirectory() )
					{
						this.filetype = "D";
					} else if( atts.isRegularFile() )
					{
						this.filetype = "F";
					} else if( atts.isSymbolicLink() )
					{
						this.filetype = "L";
					}

					Set< PosixFilePermission > perms = atts.permissions();

					this.permissions = PosixFilePermissions.toString( perms );

					this.filesize = atts.size();

					this.modified = new Timestamp( atts.lastModifiedTime().toMillis() );
					this.created = new Timestamp( atts.creationTime().toMillis() );
					this.ignore = false;

					this.cpdate = new Timestamp( System.currentTimeMillis() );

				}

				if( this.filetype.equals( "F" ) )
				{
					FileInputStream fis = new FileInputStream( new File( path.toAbsolutePath().toString() ) );
					this.filehash = org.apache.commons.codec.digest.DigestUtils.md5Hex( fis );
					fis.close();
				} else
				{
					this.filehash = "00000000000000000000000000000000";
				}

			}

		}

		public void insert( Connection conn ) throws Exception
		{
			String selectsql = "insert into fileinfo ( fullpath, filetype, permissions, filesize, modified, created, ignore, filehash, cpdate ) values (?,?,?,?,?,?,?,?,?)";
			PreparedStatement pstmt = conn.prepareStatement( selectsql );
			pstmt.setString( 1, this.fullpath );
			pstmt.setString( 2, this.filetype );
			pstmt.setString( 3, this.permissions );
			pstmt.setLong( 4, this.filesize );
			pstmt.setTimestamp( 5, this.modified );
			pstmt.setTimestamp( 6, this.created );
			pstmt.setBoolean( 7, this.ignore );
			pstmt.setString( 8, this.filehash );
			pstmt.setTimestamp( 9, this.cpdate );
			int res = pstmt.executeUpdate();
		}

		public void update( Connection conn ) throws Exception
		{
			this.cpdate = new Timestamp( System.currentTimeMillis() );

			String selectsql = "update fileinfo set filetype=?, permissions=?, filesize=?, modified=?, created=?, ignore=?, filehash=?, cpdate=? where fullpath = ?";
			PreparedStatement pstmt = conn.prepareStatement( selectsql );
			pstmt.setString( 9, this.fullpath );
			pstmt.setString( 1, this.filetype );
			pstmt.setString( 2, this.permissions );
			pstmt.setLong( 3, this.filesize );
			pstmt.setTimestamp( 4, this.modified );
			pstmt.setTimestamp( 5, this.created );
			pstmt.setBoolean( 6, this.ignore );
			pstmt.setString( 7, this.filehash );
			pstmt.setTimestamp( 8, this.cpdate );
			int res = pstmt.executeUpdate();
		}

		public void delete( Connection conn ) throws Exception
		{
			String selectsql = "insert into deletedinfo ( fullpath, filetype, permissions, filesize, modified, created, ignore, filehash, cpdate ) values (?,?,?,?,?,?,?,?,?)";
			PreparedStatement pstmt = conn.prepareStatement( selectsql );
			pstmt.setString( 1, this.fullpath );
			pstmt.setString( 2, this.filetype );
			pstmt.setString( 3, this.permissions );
			pstmt.setLong( 4, this.filesize );
			pstmt.setTimestamp( 5, this.modified );
			pstmt.setTimestamp( 6, this.created );
			pstmt.setBoolean( 7, this.ignore );
			pstmt.setString( 8, this.filehash );
			pstmt.setTimestamp( 9, this.cpdate );
			int res = pstmt.executeUpdate();
			selectsql = "delete from fileinfo where fullpath = ?";
			pstmt = conn.prepareStatement( selectsql );
			pstmt.setString( 1, this.fullpath );
			res = pstmt.executeUpdate();
		}

		public boolean equals( DBRecord other )
		{

			if( null == other )
			{
				System.out.println( "mismatch: null object" );
				return false;
			}
			if( !this.created.equals( other.created ) )
			{
				System.out.println(
						"mismatch: created " + this.created.toString() + " vs " + other.created.toString() );
				return false;
			}
			if( !this.modified.equals( other.modified ) )
			{
				System.out.println( "mismatch: modified" );
				return false;
			}
			if( !this.fullpath.equals( other.fullpath ) )
			{
				System.out.println( "mismatch: fullpath" );
				return false;
			}
			if( !this.filehash.equals( other.filehash ) )
			{
				System.out.println( "mismatch: filehash" );
				return false;
			}
			if( !this.filesize.equals( other.filesize ) )
			{
				System.out.println( "mismatch: filesize" );
				return false;
			}
			if( !this.permissions.equals( other.permissions ) )
			{
				System.out.println( "mismatch: perms" );
				return false;
			}
			if( !this.filetype.equals( other.filetype ) )
			{
				System.out.println( "mismatch: filetype" );
				return false;
			}

			return true;
		}
	}
}

// abstract the database into a class
// database structure:
/*
 * FILE TABLE ========== full path file type permission mask size modified
 * created ignore flag hash
 * 
 * while looping, if file/dir doesn't exist, error while looping, if file/dir
 * has changed, error
 * 
 * report new files, changed files, deleted files
 */