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
package com.servoy.extensions.plugins.pdf_forms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.servoy.extensions.plugins.pdf_forms.servlets.PDFServlet;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.plugins.IServerPlugin;

public class PDFFormsPluginTest
{
	@Test
	public void theServletIsRegistered() throws Exception
	{
		IServerAccess access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(new Properties());
		PDFFormsPlugin plugin = new PDFFormsPlugin();
		plugin.load();

		plugin.initialize(access);

		verify(access).registerWebService(eq("pdf_forms"), any(PDFServlet.class));
		assertEquals("PDF Forms Plugin", plugin.getProperties().get(IServerPlugin.DISPLAY_NAME));
		assertTrue(plugin.getRequiredPropertyNames().containsKey(PDFServlet.SERVER_NAME_PROPERTY));
		assertTrue(plugin.getRequiredPropertyNames().containsKey(PDFServlet.TEMPLATE_LOCATION_PROPERTY));
		plugin.unload();
	}
}
