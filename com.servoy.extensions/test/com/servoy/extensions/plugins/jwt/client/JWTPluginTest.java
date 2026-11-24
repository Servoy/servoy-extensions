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
package com.servoy.extensions.plugins.jwt.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.servoy.j2db.plugins.IClientPlugin;
import com.servoy.j2db.plugins.IClientPluginAccess;

public class JWTPluginTest
{
	private final JWTPlugin plugin = new JWTPlugin();

	@Test
	public void exposesNameAndDisplayName() throws Exception
	{
		plugin.load();
		assertEquals("jwt", plugin.getName());
		assertEquals("JWT Plugin", plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertEquals(plugin.getIconUrl() == null, plugin.getImage() == null);
		plugin.propertyChange(null);
	}

	@Test
	public void initializeStoresTheAccessAndUnloadDropsIt() throws Exception
	{
		IClientPluginAccess access = mock(IClientPluginAccess.class);
		plugin.initialize(access);
		Object provider = plugin.getScriptObject();

		assertSame(access, plugin.getClientPluginAccess());
		assertInstanceOf(JWTProvider.class, provider);
		assertSame(provider, plugin.getScriptObject());

		plugin.unload();

		assertNull(plugin.getClientPluginAccess());
		assertNotSame(provider, plugin.getScriptObject());
	}

	@Test
	public void returnedTypesAndClaimConstants()
	{
		assertArrayEquals(new Class< ? >[] { JWTClaims.class }, new JWTProvider().getAllReturnedTypes());
		assertEquals("sub", JWTClaims.SUB);
		assertEquals("iss", JWTClaims.ISS);
		assertEquals("aud", JWTClaims.AUD);
	}
}
