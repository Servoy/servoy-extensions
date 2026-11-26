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
package com.servoy.extensions.plugins.excelxport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.servoy.extensions.plugins.tabxport.FakeFoundSet;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.plugins.ClientPluginAccessProvider;
import com.servoy.j2db.plugins.IClientPlugin;

/**
 * The excel export scripting API: the .xls bytes are read back with POI (sheet, header, start row/column, column
 * names, templates and cell types).
 */
public class ExcelExportTest
{
	private ExcelXportPlugin plugin;
	private Enabler enabler;

	@BeforeEach
	public void setUp() throws Exception
	{
		ClientPluginAccessProvider access = mock(ClientPluginAccessProvider.class);
		when(access.getApplication()).thenReturn(mock(IApplication.class));
		plugin = new ExcelXportPlugin();
		plugin.load();
		plugin.initialize(access);
		enabler = (Enabler)plugin.getScriptObject();
	}

	private static final Date BORN = new GregorianCalendar(1980, 0, 2).getTime();

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
			row("id", Integer.valueOf(1), "name", "Ann", "born", BORN), //
			row("id", Integer.valueOf(2), "name", "Bob", "born", null)));
	}

	private static Sheet sheet(byte[] xls, String name) throws Exception
	{
		HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(xls));
		Sheet sheet = workbook.getSheet(name);
		assertNotNull(sheet, "no sheet " + name);
		return sheet;
	}

	// --- plugin ---

	@Test
	public void plugin() throws Exception
	{
		assertEquals("excelxport", plugin.getName());
		assertNotNull(plugin.getProperties().get(IClientPlugin.DISPLAY_NAME));
		assertNull(plugin.getPreferencePanels());
		assertNotNull(plugin.getIconUrl());
		assertNotNull(plugin.getImage());
		plugin.propertyChange(new java.beans.PropertyChangeEvent(this, "solution", null, "x"));
		// one scripting object per client; without the (smart client) menu items import/export is never enabled
		assertSame(enabler, plugin.getScriptObject());
		assertFalse(enabler.js_getExportEnabled());
		assertFalse(enabler.js_getImportEnabled());
		enabler.js_setExportEnabled(true);
		enabler.js_setImportEnabled(true);
		assertFalse(enabler.js_getExportEnabled());
		assertFalse(enabler.js_getImportEnabled());
		plugin.unload();
	}

	// --- export ---

	@Test
	public void theDefaultExport() throws Exception
	{
		byte[] xls = enabler.js_excelExport(people(), new String[] { "id", "name", "born" });

		Sheet sheet = sheet(xls, "Servoy data");
		Row header = sheet.getRow(0);
		assertEquals("id", header.getCell(0).getStringCellValue());
		assertEquals("born", header.getCell(2).getStringCellValue());
		Row ann = sheet.getRow(1);
		assertEquals(1, ann.getCell(0).getNumericCellValue());
		assertEquals("Ann", ann.getCell(1).getStringCellValue());
		assertEquals(BORN, ann.getCell(2).getDateCellValue());
		assertEquals(16, ann.getCell(2).getCellStyle().getDataFormat());
		// an empty value is an empty string cell
		assertEquals(CellType.STRING, sheet.getRow(2).getCell(2).getCellType());
		assertEquals("", sheet.getRow(2).getCell(2).getStringCellValue());
		assertEquals(2, sheet.getLastRowNum());
	}

	@Test
	public void aSheetNameAndOutputColumnNames() throws Exception
	{
		byte[] xls = enabler.js_excelExport(people(), new String[] { "id", "name" }, null, "People", new String[] { "Number", "Name" });

		Row header = sheet(xls, "People").getRow(0);
		assertEquals("Number", header.getCell(0).getStringCellValue());
		assertEquals("Name", header.getCell(1).getStringCellValue());
	}

	@Test
	public void theStartRowAndColumnAreOneBased() throws Exception
	{
		byte[] xls = enabler.js_excelExport(people(), new String[] { "id" }, null, "Data", Integer.valueOf(3), Integer.valueOf(2));
		byte[] rowOnly = enabler.js_excelExport(people(), new String[] { "id" }, null, "Data", Integer.valueOf(5));
		byte[] named = enabler.js_excelExport(people(), new String[] { "id" }, null, "Data", new String[] { "Nr" }, Integer.valueOf(2));
		byte[] defaults = enabler.js_excelExport(people(), new String[] { "id" }, null, "Data", (Number)null, (Number)null);

		Sheet sheet = sheet(xls, "Data");
		assertNull(sheet.getRow(0));
		assertEquals("id", sheet.getRow(2).getCell(1).getStringCellValue());
		assertEquals(2, sheet.getRow(4).getCell(1).getNumericCellValue());
		assertEquals("id", sheet(rowOnly, "Data").getRow(4).getCell(0).getStringCellValue());
		assertEquals("Nr", sheet(named, "Data").getRow(1).getCell(0).getStringCellValue());
		assertEquals("id", sheet(defaults, "Data").getRow(0).getCell(0).getStringCellValue());
	}

	@Test
	public void aTemplateIsFilledIn() throws Exception
	{
		HSSFWorkbook template = new HSSFWorkbook();
		template.createSheet("Cover").createRow(0).createCell(0).setCellValue("Report");
		template.createSheet("Data").createRow(10).createCell(0).setCellValue("kept");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		template.write(out);

		byte[] xls = enabler.js_excelExport(people(), new String[] { "name" }, out.toByteArray(), "Data");

		HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(xls));
		assertEquals("Report", workbook.getSheet("Cover").getRow(0).getCell(0).getStringCellValue());
		assertEquals("Ann", workbook.getSheet("Data").getRow(1).getCell(0).getStringCellValue());
		assertEquals("kept", workbook.getSheet("Data").getRow(10).getCell(0).getStringCellValue());
		assertTrue(workbook.getSheet("Data").isActive());
		assertEquals(2, workbook.getNumberOfSheets());
	}

	@Test
	public void aTemplateWithoutTheSheetGetsANewOne() throws Exception
	{
		byte[] first = enabler.js_excelExport(people(), new String[] { "id" }, null, "First");

		byte[] xls = enabler.js_excelExport(people(), new String[] { "name" }, first);

		HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(xls));
		assertEquals(2, workbook.getNumberOfSheets());
		assertEquals("Bob", workbook.getSheet("Servoy data").getRow(2).getCell(0).getStringCellValue());
	}

	@Test
	public void withoutSheetNameTheDefaultNameIsUsed() throws Exception
	{
		HSSFWorkbook workbook = ExcelExportUtil.populateWb(people(), new String[] { "id" }, null, null, null, 0, 0);

		// current behaviour: the default sheet name of populateWb ("Servoy Data") differs in case from the one of
		// excelExport ("Servoy data")
		assertNotNull(workbook.getSheet("Servoy Data"));
	}

	@Test
	public void columnNamesMustMatchTheDataProviders()
	{
		assertThrows(RuntimeException.class,
			() -> enabler.js_excelExport(people(), new String[] { "id", "name" }, null, "x", new String[] { "only one" }));
	}

	@Test
	public void nothingToExport() throws Exception
	{
		assertNull(enabler.js_excelExport(null, new String[] { "id" }));
		assertNull(enabler.js_excelExport(people(), null));
		assertNull(enabler.js_excelExport(people(), new String[0]));
	}

	@Test
	public void aTemplateThatIsNotAnExcelFileFails()
	{
		assertThrows(Exception.class, () -> enabler.js_excelExport(people(), new String[] { "id" }, "not excel".getBytes()));
	}

	@Test
	public void theSameDateStyleIsReused() throws Exception
	{
		IFoundSet dates = FakeFoundSet.of(List.of(row("d", BORN), row("d", BORN)));

		Sheet sheet = sheet(enabler.js_excelExport(dates, new String[] { "d" }), "Servoy data");

		assertSame(sheet.getRow(1).getCell(0).getCellStyle().getIndex() == sheet.getRow(2).getCell(0).getCellStyle().getIndex() ? Boolean.TRUE
			: Boolean.FALSE, Boolean.TRUE);
	}
}
