/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Copies to fragment to the query string and redirects to the solution.
 * @author emera
 */
@SuppressWarnings("nls")
public class OAuthLandingServlet extends HttpServlet
{
	private final OAuthPlugin plugin;

	public OAuthLandingServlet(OAuthPlugin plugin)
	{
		this.plugin = plugin;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{
		String scheme = req.getScheme();
		String serverName = req.getServerName();
		int serverPort = req.getServerPort();
		String contextPath = req.getContextPath();
		String pathInfo = req.getPathInfo().replaceFirst("/oauth", "");
		String queryString = req.getQueryString();

		StringBuilder url = new StringBuilder();
		url.append(scheme).append("://").append(serverName);

		if (serverPort != 80 && serverPort != 443)
		{
			url.append(":").append(serverPort);
		}
		url.append(contextPath);
		if (pathInfo != null)
		{
			url.append(pathInfo);
		}
		url.append("?");
		if (queryString != null)
		{
			url.append(queryString);
		}

		resp.setContentType("text/html");
		PrintWriter out = resp.getWriter();

		out.println("<html>");
		out.println("<head>");
		out.println("<script type=\"text/javascript\">");
		out.println("function redirectToSolution() {");
		out.println(" var url = '" + url + "'+window.location.hash.substring(1);");
		out.println("  window.location.href = url;");
		out.println("  }");
		out.println(" window.onload = redirectToSolution;");
		out.println(" </script>");
		out.println("</head>");
		out.println("<body>");
		out.println("</body>");
		out.println("</html>");
	}
}
