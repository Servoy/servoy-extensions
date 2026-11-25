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
package com.servoy.extensions.plugins.oauth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;

import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.INGClientPluginAccess;
import com.servoy.j2db.solutionmodel.ISolutionModel;

/**
 * A mocked NG client for the oauth plugin: server url, solution name, solution model and executor. No network.
 */
final class OAuthTestSupport
{
	final INGClientPluginAccess access = mock(INGClientPluginAccess.class);
	final ISolutionModel solutionModel = mock(ISolutionModel.class);
	final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	final Map<Object, Object> runtimeProperties = new HashMap<>();
	final OAuthPlugin plugin = new OAuthPlugin();
	final OAuthProvider provider;

	OAuthTestSupport() throws Exception
	{
		this("https://example.com");
	}

	OAuthTestSupport(String serverUrl) throws Exception
	{
		when(access.getServerURL()).thenReturn(new URL(serverUrl));
		when(Integer.valueOf(access.getApplicationType())).thenReturn(Integer.valueOf(IClientPluginAccess.NG_CLIENT));
		when(access.getMainSolutionName()).thenReturn("mysol");
		when(access.getRuntimeProperties()).thenReturn(runtimeProperties);
		when(access.getSolutionModel()).thenReturn(solutionModel);
		when(access.getExecutor()).thenReturn(executor);
		plugin.initialize(access);
		provider = (OAuthProvider)plugin.getScriptObject();
	}

	/** A script function that FunctionDefinition resolves to scopes.globals.onAuth. */
	static Function callback()
	{
		Function f = mock(Function.class);
		when(f.get("_scopename_", f)).thenReturn("globals");
		when(f.get("_methodname_", f)).thenReturn("onAuth");
		return f;
	}

	static NativeObject object(String... keyValues)
	{
		NativeObject object = new NativeObject();
		for (int i = 0; i < keyValues.length; i += 2)
		{
			object.put(keyValues[i], object, keyValues[i + 1]);
		}
		return object;
	}

	static String decode(String url)
	{
		return URLDecoder.decode(url, StandardCharsets.UTF_8);
	}
}
