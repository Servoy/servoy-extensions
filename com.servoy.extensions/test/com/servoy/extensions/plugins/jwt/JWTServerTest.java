/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */
package com.servoy.extensions.plugins.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;
import com.servoy.j2db.server.shared.IClientInformation;

public class JWTServerTest
{
	private static final String SECRET_KEY = "jwt.secret.password";
	private static final String EXTRA_KEY = "jwt.svy21824.test.extra";

	private IServerAccess serverAccess;
	private Properties settings;
	private JWTServer server;

	@BeforeEach
	public void setUp() throws Exception
	{
		settings = new Properties();
		settings.setProperty(SECRET_KEY, "s3cret");
		serverAccess = mock(IServerAccess.class);
		when(serverAccess.getSettings()).thenReturn(settings);
		server = new JWTServer();
	}

	@AfterEach
	public void tearDown()
	{
		System.clearProperty(SECRET_KEY);
		System.clearProperty(EXTRA_KEY);
	}

	private static IClientInformation client(String id, int applicationType)
	{
		IClientInformation info = mock(IClientInformation.class);
		when(info.getClientID()).thenReturn(id);
		when(Integer.valueOf(info.getApplicationType())).thenReturn(Integer.valueOf(applicationType));
		return info;
	}

	@Test
	public void exposesDisplayNameAndTheRequiredSecretProperty() throws Exception
	{
		server.load();
		assertEquals("JWT Plugin", server.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertEquals(1, server.getRequiredPropertyNames().size());
		assertTrue(server.getRequiredPropertyNames().containsKey(SECRET_KEY));
	}

	@Test
	public void initializeRegistersTheRemoteService() throws Exception
	{
		server.initialize(serverAccess);
		verify(serverAccess).registerRemoteService(IJWTService.class.getName(), server);
	}

	@Test
	public void initializeCopiesNonBlankJwtSettingsToSystemProperties() throws Exception
	{
		settings.setProperty(EXTRA_KEY, "  ");
		settings.setProperty("other.key", "x");

		server.initialize(serverAccess);

		assertEquals("s3cret", System.getProperty(SECRET_KEY));
		assertNull(System.getProperty(EXTRA_KEY));
		assertNull(System.getProperty("other.key"));
	}

	@Test
	public void initializeSurvivesAFailingRemoteServiceRegistration() throws Exception
	{
		doThrow(new RemoteException("rmi down")).when(serverAccess).registerRemoteService(anyString(), any());
		server.initialize(serverAccess);
		assertEquals("s3cret", System.getProperty(SECRET_KEY));
	}

	@Test
	public void secretIsGivenToConnectedNonSmartClients() throws Exception
	{
		IClientInformation ng = client("ng-1", IApplication.NG_CLIENT);
		when(serverAccess.getConnectedClients()).thenReturn(new IClientInformation[] { ng });
		server.initialize(serverAccess);

		assertEquals("s3cret", server.getSecret("ng-1"));
	}

	@Test
	public void secretIsNotGivenToASmartClient() throws Exception
	{
		IClientInformation smart = client("sc-1", IApplication.CLIENT);
		when(serverAccess.getConnectedClients()).thenReturn(new IClientInformation[] { smart });
		server.initialize(serverAccess);

		assertNull(server.getSecret("sc-1"));
	}

	@Test
	public void secretIsGivenToASmartClientThatIsAServerProcess() throws Exception
	{
		IClientInformation smart = client("sc-1", IApplication.CLIENT);
		when(serverAccess.getConnectedClients()).thenReturn(new IClientInformation[] { smart });
		when(Boolean.valueOf(serverAccess.isServerProcess("sc-1"))).thenReturn(Boolean.TRUE);
		server.initialize(serverAccess);

		assertEquals("s3cret", server.getSecret("sc-1"));
	}

	@Test
	public void secretIsNotGivenToUnknownOrMissingClients() throws Exception
	{
		IClientInformation ng = client("ng-1", IApplication.NG_CLIENT);
		when(serverAccess.getConnectedClients()).thenReturn(new IClientInformation[] { ng });
		server.initialize(serverAccess);

		assertNull(server.getSecret("unknown"));
		assertNull(server.getSecret(null));
	}

	@Test
	public void unloadDropsTheSettings() throws Exception
	{
		server.initialize(serverAccess);
		server.unload();
		when(serverAccess.getConnectedClients()).thenReturn(new IClientInformation[0]);
		assertNull(server.getSecret("x"));
	}
}
