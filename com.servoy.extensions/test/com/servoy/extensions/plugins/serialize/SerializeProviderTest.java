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
package com.servoy.extensions.plugins.serialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.plugins.IClientPluginAccess;

public class SerializeProviderTest
{
	private SerializeProvider provider;

	@BeforeEach
	public void setUp() throws Exception
	{
		SerializePlugin plugin = new SerializePlugin();
		plugin.initialize(mock(IClientPluginAccess.class));
		provider = (SerializeProvider)plugin.getScriptObject();
	}

	@Test
	public void toJSONQuotesStrings() throws Exception
	{
		assertEquals("\"abc\"", provider.js_toJSON("abc"));
	}

	@Test
	public void fromJSONParsesWhatToJSONProduced() throws Exception
	{
		Map<String, Object> map = new HashMap<>();
		map.put("name", "servoy");

		assertEquals(map, provider.js_fromJSON(provider.js_toJSON(map)));
		assertEquals("abc", provider.js_fromJSON(provider.js_toJSON("abc")));
	}
}
