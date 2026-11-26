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
package com.servoy.extensions.plugins.tabxport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.plugins.IClientPlugin;

/**
 * The text export scripting API (textExport and the TabExporter) on a mocked foundset.
 */
public class TextExportTest
{
	private IApplication application;
	private TextXportPlugin plugin;
	private Enabler enabler;

	@BeforeEach
	public void setUp() throws Exception
	{
		application = mock(IApplication.class);
		when(application.getI18NMessageIfPrefixed(anyString())).thenAnswer(inv -> {
			String key = inv.getArgument(0);
			return key.startsWith("i18n:") ? "translated " + key.substring(5) : key;
		});
		ClientPluginAccessProvider access = mock(ClientPluginAccessProvider.class);
		when(access.getApplication()).thenReturn(application);
		plugin = new TextXportPlugin();
		plugin.load();
		plugin.initialize(access);
		enabler = (Enabler)plugin.getScriptObject();
	}

	private static Map<String, Object> row(Object... keyValues)
	{
		Map<String, Object> row = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2)
		{
			row.put((String)keyValues[i], keyValues[i + 1]);
		}
		return row;
	}

	private static IFoundSet people()
	{
		return FakeFoundSet.of(List.of( //
			row("id", Integer.valueOf(1), "name", "Ann", "born", new GregorianCalendar(1980, 0, 2).getTime(), "salary", new BigDecimal("1234.5")), //
			row("id", Integer.valueOf(2), "name", "Bob \"B\" Brown", "born", null, "salary", Double.valueOf(99.25)), //
			row("id", Integer.valueOf(3), "name", "", "born", null, "salary", null)));
	}

	// --- plugin ---

	@Test
	public void plugin() throws Exception
	{
		assertEquals("textxport", plugin.getName());
		assertNotNull(plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		assertSame(enabler, plugin.getScriptObject());
		assertEquals(List.of(TabExporter.class, DataProviderExport.class), Arrays.asList(enabler.getAllReturnedTypes()));
		plugin.propertyChange(new java.beans.PropertyChangeEvent(this, "solution", null, "x"));
		// outside the smart client there are no import/export menu items
		assertFalse(enabler.js_getExportEnabled());
		assertFalse(enabler.js_getImportEnabled());
		enabler.js_setExportEnabled(true);
		enabler.js_setImportEnabled(true);
		assertFalse(enabler.js_getExportEnabled());
		plugin.unload();
	}

	// --- textExport ---

	@Test
	public void tabSeparatedWithoutHeader()
	{
		String export = enabler.js_textExport(people(), new String[] { "id", "name" });

		assertEquals("1\t\"Ann\"\n2\t\"Bob \"\"B\"\" Brown\"\n3\t\n", export);
	}

	@Test
	public void ownSeparatorAndHeader()
	{
		String export = enabler.js_textExport(people(), new String[] { "id", "salary" }, ";", true);

		assertEquals("\"id\";\"salary\"\n1;1234.5\n2;99.25\n3;\n", export);
		assertEquals("1;1234.5\n2;99.25\n3;\n", enabler.js_textExport(people(), new String[] { "id", "salary" }, ";"));
	}

	@Test
	public void datesAreQuoted()
	{
		String export = enabler.js_textExport(FakeFoundSet.of(List.of(row("born", new GregorianCalendar(1980, 0, 2).getTime()))),
			new String[] { "born" });

		assertEquals('"', export.charAt(0));
		assertEquals("\"\n", export.substring(export.length() - 2));
	}

	@Test
	public void nothingToExport()
	{
		assertNull(enabler.js_textExport(null, new String[] { "id" }));
		assertNull(enabler.js_textExport(people(), null));
		assertNull(enabler.js_textExport(people(), new String[0]));
		assertEquals("", enabler.js_textExport(FakeFoundSet.of(List.of()), new String[] { "id" }));
	}

	// --- TabExporter ---

	@Test
	public void theExporterUsesHeaderTextsAndFormats()
	{
		TabExporter exporter = enabler.js_createExporter(people(), ",", true);
		exporter.js_addDataProvider("id").js_setHeaderText("Number").js_setFormat("000");
		exporter.js_addDataProvider("born").js_setHeaderText("i18n:birthday").js_setFormat("yyyy-MM-dd");
		exporter.js_addDataProvider("salary").js_setFormat("#,##0.00");
		exporter.js_addDataProvider("name");

		String export = exporter.js_textExport();

		String[] lines = export.split("\n");
		assertEquals("\"Number\",\"translated birthday\",\"salary\",\"name\"", lines[0]);
		// the number format uses the default locale
		String salary = new java.text.DecimalFormat("#,##0.00").format(new BigDecimal("1234.5"));
		String smallSalary = new java.text.DecimalFormat("#,##0.00").format(99.25);
		// formatted values are strings and quoted on both sides, unformatted numbers stay unquoted
		assertEquals("\"001\",\"1980-01-02\",\"" + salary + "\",\"Ann\"", lines[1]);
		assertEquals("\"002\",,\"" + smallSalary + "\",\"Bob \"\"B\"\" Brown\"", lines[2]);
		assertEquals("\"003\",,,", lines[3]);
	}

	@Test
	public void theExporterDefaultsToTabsWithoutHeader()
	{
		TabExporter exporter = enabler.js_createExporter(people(), null, false);
		exporter.js_addDataProvider("id");

		assertEquals("1\n2\n3\n", exporter.js_textExport());
	}

	@Test
	public void anInvalidFormatIsOnlyLogged()
	{
		TabExporter exporter = enabler.js_createExporter(people(), ";", false);
		exporter.js_addDataProvider("born").js_setFormat("invalid 'pattern");

		// the date is written as it is
		assertEquals('"', exporter.js_textExport().charAt(0));
	}

	@Test
	public void anUnknownValueListLeavesTheValues()
	{
		when(application.getFlattenedSolution()).thenReturn(mock(FlattenedSolution.class));
		TabExporter exporter = enabler.js_createExporter(people(), ";", false);
		exporter.js_addDataProvider("id").js_setValueList("no_such_valuelist");
		exporter.js_addDataProvider("name");

		assertEquals("1;\"Ann\"\n2;\"Bob \"\"B\"\" Brown\"\n3;\n", exporter.js_textExport());
	}

	@Test
	public void anExporterWithoutDataProvidersExportsNothing()
	{
		assertNull(enabler.js_createExporter(people(), ";", true).js_textExport());
		assertNull(enabler.js_createExporter(null, ";", true).js_textExport());
	}

	@Test
	public void theHeaderHelper()
	{
		assertEquals("\"a\"|\"b\"\n", TextExportUtil.createHeader(new String[] { "a", "b" }, "|").toString());
		assertEquals("\n", TextExportUtil.createHeader(new String[0], "|").toString());
	}
}
