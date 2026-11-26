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

import java.sql.Types;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.component.ComponentFactory;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.dataprocessing.IRecord;
import com.servoy.j2db.dataprocessing.IValueList;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * Creates the (tab/character separated) text of a text export.
 *
 * @author jblok
 */
final class TextExportUtil
{
	private TextExportUtil()
	{
	}

	static StringBuffer createHeader(String[] dataProviders, String sep)
	{
		StringBuffer headerBuffer = new StringBuffer();
		for (int k = 0; k < dataProviders.length; k++)
		{
			headerBuffer.append("\""); //$NON-NLS-1$
			headerBuffer.append(dataProviders[k]);
			headerBuffer.append("\""); //$NON-NLS-1$
			if (k < dataProviders.length - 1) headerBuffer.append(sep);
		}
		headerBuffer.append("\n"); //$NON-NLS-1$
		return headerBuffer;
	}

	static StringBuffer populateFileData(IApplication application, IFoundSet foundSet, String[] dataProviders, String sep, String[] formats,
		String[] valuelists)
	{
		IValueList[] runtimeValuelists = new IValueList[dataProviders.length];
		if (application != null && valuelists != null)
		{
			for (int i = 0; i < dataProviders.length; i++)
			{
				if (valuelists[i] != null)
				{
					ValueList vl = application.getFlattenedSolution().getValueList(valuelists[i]);
					if (vl != null)
					{
						runtimeValuelists[i] = ComponentFactory.getRealValueList(application, vl, true, Types.OTHER, null, null, true);
					}
				}
			}
		}
		StringBuffer fData = new StringBuffer();
		for (int i = 0; i < foundSet.getSize(); i++)
		{
			IRecord s = foundSet.getRecord(i);
			for (int k = 0; k < dataProviders.length; k++)
			{
				Object obj = s.getValue(dataProviders[k]);
				if (runtimeValuelists[k] != null)
				{
					int index = runtimeValuelists[k].realValueIndexOf(obj);
					if (index != -1)
					{
						obj = runtimeValuelists[k].getElementAt(index);
					}
				}
				// strings and dates are quoted; decide that after formatting, so the opening and closing quote always match
				// (a formatted number becomes a string and is quoted on both sides, it may contain the separator)
				boolean isDate = obj instanceof Date;
				if (obj != null)
				{
					if (formats != null && formats[k] != null)
					{
						try
						{
							if (obj instanceof Date)
							{
								SimpleDateFormat dformatter = new SimpleDateFormat(formats[k]);
								obj = dformatter.format((Date)obj);
							}
							else if (obj instanceof Number)
							{
								DecimalFormat nformatter = new DecimalFormat(formats[k]);
								obj = nformatter.format(obj);
							}
						}
						catch (Exception ex)
						{
							Debug.error(ex);
						}
					}
				}
				boolean quote = isDate || (obj instanceof String && ((String)obj).length() != 0);
				if (quote) fData.append("\""); //$NON-NLS-1$
				if (obj instanceof String) obj = Utils.stringReplace((String)obj, "\"", "\"\""); //$NON-NLS-1$ //$NON-NLS-2$
				if (obj != null) fData.append(obj);
				if (quote) fData.append("\""); //$NON-NLS-1$
				if (k < dataProviders.length - 1) fData.append(sep);
			}
			fData.append("\n"); //$NON-NLS-1$
		}
		return fData;
	}
}
