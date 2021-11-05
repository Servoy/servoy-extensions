/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2019 Servoy BV

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

package com.servoy.extensions.plugins.oauth.apis;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.Verb;

/**
 * @author emera
 */
public class IntuitApi extends DefaultApi20
{
	protected IntuitApi()
	{
	}

	private static class InstanceHolder
	{
		private static final IntuitApi INSTANCE = new IntuitApi();
	}

	public static IntuitApi instance()
	{
		return InstanceHolder.INSTANCE;
	}

	@Override
	public Verb getAccessTokenVerb()
	{
		return Verb.POST;
	}

	@Override
	public String getAccessTokenEndpoint()
	{
		return "https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer";
	}

	@Override
	protected String getAuthorizationBaseUrl()
	{
		return "https://appcenter.intuit.com/connect/oauth2";
	}


	@Override
	public String getRevokeTokenEndpoint()
	{
		return "https://developer.api.intuit.com/v2/oauth2/tokens/revoke";
	}
}
