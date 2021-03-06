/*
 *+------------------------------------------------------------------------+
 *| Licensed Materials - Property of IBM                                   |
 *| (C) Copyright IBM Corp. 2017.  All Rights Reserved.                    |
 *|                                                                        |
 *| US Government Users Restricted Rights - Use, duplication or disclosure |
 *| restricted by GSA ADP Schedule Contract with IBM Corp.                 |
 *+------------------------------------------------------------------------+
 */
package com.ibm.repotools.utilities;

import java.io.Console;
import java.util.Collection;
import java.util.Iterator;
import java.util.Scanner;

import javax.naming.NamingException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.util.ObfuscationHelper;

import com.ibm.repotools.utilities.Status;

/** A program to synchronize LDAP (RACF) and RTC users.
 *   * Server users based on repository permissions (JazzAdmins, JazzUsers, etc.)
 *   * Client access license assignment and unassignment
 *   * Project and Team Area Administrators and Members
 *   * Project and Team Area process roles
 * 
 * @author jamsden
 *
 */
public class LDAP2RTCSync {

	private Logger log = LoggerFactory.getLogger(LDAP2RTCSync.class);

	private LdapRtcConfig config = null;  		// The LDAP-RTC synchronization JSON configuration file.
	private LdapConnection connection = null; 	// The LDAP directory connection

	/** The main program entry point to synchronize LDAP and RTC users.
	 * 
	 * @param args --config <configuration-file>.json
	 * @throws TeamRepositoryException
	 */
	public static void main(String[] args) throws TeamRepositoryException {
		LDAP2RTCSync synchronizer = new LDAP2RTCSync();
		synchronizer.log.info("Synchronizing LDAP and RTC Users");
		try {
			if (synchronizer.initialize(args)) {
				synchronizer.sync();
			} else {
				Status.appStatus.setCode(-2); // not enough information to run
			}
		} catch (Exception e) {
			e.printStackTrace();
			Status.appStatus.setCode(-1);
		} finally {
			TeamPlatform.shutdown();
		}
		synchronizer.log.info("Done");
		System.exit(Status.appStatus.getCode());
	}

	/** Synchronizes LDAP (RACF) and RTC users
	 *   
	 * @param args --config <configuration-file>.json
	 * @return
	 * @throws ParseException
	 */
	public boolean initialize(String[] args) throws ParseException {

		try {
			Options options = new Options();
			options.addOption("c", "config", true, "LDAP - RTC users configuration file");
			options.addOption("e", "encrypt", false, "Encrypt passwords to be used in the configuration file");

			CommandLineParser parser = new PosixParser();
			CommandLine cmd = parser.parse(options, args);
			
			if (cmd.hasOption("e")) {
				Console console = System.console();
				String passwd = null;
				if (console == null) {
					Scanner scanner = new Scanner(System.in);
					System.out.println("Enter password to encrypt:");
					passwd = scanner.nextLine();
					scanner.close();
				} else {
					passwd = new String(console.readPassword("Enter password to encrypt:"));
				}
				System.out.println(ObfuscationHelper.encryptString(passwd));
				System.exit(0);
			}

			String configFile = cmd.getOptionValue("c");

			if (configFile == null) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("LDP2RTCSync", options);
				return false;
			}
			
			config = new LdapRtcConfig(configFile, log);  // read and process the configuration file
			if (config==null) {
				log.error("Unable to read: "+configFile);
				return false;
			}
			
			connection = config.getLDAPConnection();
			if (connection==null) {
				log.error("Missing LDAPConnection element in config file");
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;

	}
	
	public Collection<RTCServer> getServers() {
		return config.getServers();
	}
	
		
	/** Does the LDAP - RTC user synchronization
	 * 
	 * @throws TeamRepositoryException
	 */
	public void sync() throws TeamRepositoryException {
		try {
			// Synchronize each RTC server specified in the configuration file.
			Iterator<RTCServer> servers = config.getServers().iterator();
			while (servers.hasNext()) {
				RTCServer server = servers.next();
				log.info("Synchronizing users for server: "+server.getServerURI());
				server.syncServerUsers();
				server.disconnect();
			}
		} catch (NamingException e) {
			e.printStackTrace();
			Status.appStatus.setCode(-1);
		}
	}
}
