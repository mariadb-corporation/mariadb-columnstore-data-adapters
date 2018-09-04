/*
 * Copyright (c) 2018 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2021-06-15
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

package com.mariadb.adapter.columnstorebulkconnector.metadata.adapter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.mariadb.jdbc.MariaDbConnection;

import com.informatica.sdk.adapter.metadata.common.Status;
import com.informatica.sdk.adapter.metadata.common.StatusEnum;
import com.informatica.sdk.adapter.metadata.provider.AbstractConnection;

public class ColumnStoreBulkConnectorConnection extends AbstractConnection  {

	private Connection conn;
	private String database;
	private final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";

	/**
	 * Establishes a connection with the external data source.
	 * 
	 * @param connAttrs
	 *			The list of connection attributes.
	 * @return The Status of the connection.
	 */

	@Override
	public Status openConnection(Map<String, Object> connAttrs){
		Status status;
		String username = (String) connAttrs.get("username");
		String password = (String) connAttrs.get("password");
		String host = (String) connAttrs.get("host");
		int port = (int) connAttrs.get("port");
		this.database = (String) connAttrs.get("database");
		status = new Status(StatusEnum.SUCCESS, null);
				
		String connectionURL = "jdbc:mariadb://" + host + ":" + Integer.toString(port) + "/" + database;
		try {
			//JDBC connection
			DriverClassLoader driverClassLoader = new DriverClassLoader(MariaDbConnection.class.getClassLoader());
			Driver driver = (Driver) Class.forName(JDBC_DRIVER, true, driverClassLoader).newInstance();
			DriverManager.registerDriver(new DriverWrapper(driver));

			conn = DriverManager.getConnection(connectionURL, username, password);
			
		} catch (Exception e) {
			e.printStackTrace();
			if (e.getMessage().contains("Communications link failure")) {
				return new Status(StatusEnum.FAILURE, "Invalid hostname or port number");
			}
			return new Status(StatusEnum.FAILURE, e.getMessage());
		}
		return status;
	}


	/**
	 * Closes the connection of the data source.
	 * 
	 * @return The Status of the connection.
	 */ 

	@Override
	public Status closeConnection(){
		try {
			conn.close();
		} catch (Exception e) {
			return new Status(StatusEnum.FAILURE, e.getMessage());
		}
		return new Status(StatusEnum.SUCCESS, null);
	}



	/**
	 * Gets a MariaDB connection object
	 * 
	 * @return The MariaDB connection object.
	 */ 

	public Connection getMariaDBConnection(){
		return conn;
	}
	
	
	/**
	 * Gets the Database name used for the connection
	 * 
	 * @return The database name used for the connection.
	 */ 

	public String getDatabaseName(){
		return database;
	}
	
	
	/**
	 * Inner class.<br>
	 * Custom driver class loader for loading the third party jar.
	 * 
	 */

	public static class DriverClassLoader extends URLClassLoader {

		private static final String SYSTEM_CLASSPATH = System.getenv("CLASSPATH");
		private static final String INFA_JAVA_CMD_CLASSPATH = System.getenv("INFA_JAVA_CMD_CLASSPATH");
		private static final String OSNAME = "os.name";

		public DriverClassLoader(ClassLoader parent) throws MalformedURLException {
			super(getClassPathURL(parent), parent);
		}

		private static URL[] getClassPathURL(ClassLoader parentLoader) throws MalformedURLException {
			List<URL> jars;

			jars = getClassPathURL(parentLoader, INFA_JAVA_CMD_CLASSPATH);
			jars.addAll(getClassPathURL(parentLoader, SYSTEM_CLASSPATH));

			URL[] urlArray = new URL[jars.size()];
			return jars.toArray(urlArray);
		}

		private static List<URL> getClassPathURL(ClassLoader parentLoader, String classpathVariable)
				throws MalformedURLException {
			List<URL> jars = new ArrayList<URL>();
			if (classpathVariable == null)
				return jars;

			Properties prop = System.getProperties();
			String osName = ((String) prop.get(OSNAME)).toUpperCase();

			StringTokenizer st1;
			if (osName.indexOf("WIN") >= 0)
				st1 = new StringTokenizer(classpathVariable, ";");
			else
				st1 = new StringTokenizer(classpathVariable, ":");

			String jarName;
			File jarFile;
			while (st1.hasMoreTokens()) {
				jarName = st1.nextToken();
				if (jarName.endsWith("*")) {
					// it should be directory
					int len = jarName.length();
					String directoryName = jarName.substring(0, len - 1);
					File directoryFile = new File(directoryName);
					if (directoryFile.isDirectory()) {
						addDirectory(jars, directoryFile);
					}
				} else if (jarName.toUpperCase(Locale.ENGLISH).endsWith(".JAR")) {
					jarFile = new File(jarName);
					try {
						jars.add(jarFile.toURI().toURL());
					} catch (MalformedURLException e) {
						e.printStackTrace();
					}
				}
			}

			return jars;
		}

		private static void addDirectory(List<URL> jars, File directoryFile) throws MalformedURLException {
			for (File f : directoryFile.listFiles()) {
				if (f.isDirectory()) {
					addDirectory(jars, f);
				} else if (f.getName().toUpperCase(Locale.ENGLISH).endsWith(".JAR")) {
					jars.add(f.toURI().toURL());
				}
			}
		}
	}

	/**
	 * Inner class.<br>
	 * Custom driver wrapper class
	 * 
	 */

	public class DriverWrapper implements Driver {
		private Driver driver;

		public DriverWrapper(Driver d) {
			this.driver = d;
		}

		public boolean acceptsURL(String u) throws SQLException {
			return this.driver.acceptsURL(u);
		}

		public Connection connect(String u, Properties p) throws SQLException {
			return this.driver.connect(u, p);
		}

		public int getMajorVersion() {
			return this.driver.getMajorVersion();
		}

		public int getMinorVersion() {
			return this.driver.getMinorVersion();
		}

		public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
			return this.driver.getPropertyInfo(u, p);
		}

		public boolean jdbcCompliant() {
			return this.driver.jdbcCompliant();
		}

		public Logger getParentLogger() throws SQLFeatureNotSupportedException {
			return this.driver.getParentLogger();
		}
	}
}