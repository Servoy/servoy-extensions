/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2024 Servoy BV

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

/**
 * @author emera
 */
public class UPSApi extends DefaultApi20
{
	private static class InstanceHolder
	{
		private static final UPSApi INSTANCE = new UPSApi();
	}

	public static UPSApi instance()
	{
		return InstanceHolder.INSTANCE;
	}

	@Override
	public String getAccessTokenEndpoint()
	{
		return "https://wwwcie.ups.com/security/v1/oauth/token";
	}

	@Override
	protected String getAuthorizationBaseUrl()
	{
		return "https://wwwcie.ups.com/security/v1/oauth/authorize";
	}

	@Override
	public String getRefreshTokenEndpoint()
	{
		return "https://apis-pt.ups.com/security/v1/oauth/refresh";
	}
}