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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.dataprocessing.IRecord;

/**
 * Fills an (xls) workbook with the data of a foundset.
 *
 * @author jblok
 */
final class ExcelExportUtil
{
	private ExcelExportUtil()
	{
	}

	static HSSFWorkbook populateWb(IFoundSet foundSet, String[] dataProviders, byte[] templateXLS, String[] outputColumnNames, String sheetName,
		int startRow, int startColumn) throws IOException
	{
		HSSFWorkbook hwb;
		if (templateXLS == null)
		{
			hwb = new HSSFWorkbook();
		}
		else
		{
			InputStream buff = new ByteArrayInputStream(templateXLS);
			hwb = new HSSFWorkbook(buff);
		}
		if (sheetName == null) sheetName = "Servoy Data"; //$NON-NLS-1$
		HSSFSheet sheet = hwb.getSheet(sheetName);
		if (sheet == null) sheet = hwb.createSheet(sheetName);
		sheet.setActive(true);

		if (outputColumnNames != null && outputColumnNames.length != dataProviders.length)
		{
			throw new RuntimeException("The arrays 'output column names' and 'data provider ids' must have the same length."); //$NON-NLS-1$
		}
		String[] columnNames = outputColumnNames != null ? outputColumnNames : dataProviders;
		HSSFRow header = sheet.createRow(startRow);
		for (int k = 0; k < columnNames.length; k++)
		{
			HSSFCell cell = header.createCell(k + startColumn);
			cell.setCellValue(columnNames[k]);
		}
		HSSFCellStyle[] cellStyles = new HSSFCellStyle[dataProviders.length];
		for (int i = 0; i < foundSet.getSize(); i++)
		{
			HSSFRow row = sheet.createRow(i + 1 + startRow);
			IRecord s = foundSet.getRecord(i);
			for (int k = 0; k < dataProviders.length; k++)
			{
				HSSFCell cell = row.createCell(k + startColumn);

				Object obj = s.getValue(dataProviders[k]);
				if (obj instanceof Date)
				{
					if (cellStyles[k] == null)
					{
						HSSFCellStyle cellStyle = hwb.createCellStyle();
						cellStyle.setDataFormat((short)16);
						cellStyles[k] = cellStyle;
					}
					cell.setCellValue((Date)obj);
					cell.setCellStyle(cellStyles[k]);
				}
				else if (obj instanceof String)
				{
					cell.setCellValue((String)obj);
				}
				else if (obj instanceof Number)
				{
					cell.setCellValue(((Number)obj).doubleValue());
				}
				else
				{
					cell.setCellValue(""); //$NON-NLS-1$
				}
			}
		}

		return hwb;
	}
}
