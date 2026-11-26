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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.dataprocessing.IRecord;

/**
 * A mocked foundset whose records answer getValue from a map per row.
 */
public final class FakeFoundSet
{
	private FakeFoundSet()
	{
	}

	public static IFoundSet of(List<Map<String, Object>> rows)
	{
		IFoundSet foundSet = mock(IFoundSet.class);
		when(Integer.valueOf(foundSet.getSize())).thenReturn(Integer.valueOf(rows.size()));
		when(foundSet.getRecord(anyInt())).thenAnswer(inv -> {
			Map<String, Object> row = rows.get(((Integer)inv.getArgument(0)).intValue());
			IRecord record = mock(IRecord.class);
			when(record.getValue(anyString())).thenAnswer(v -> row.get(v.getArgument(0)));
			return record;
		});
		return foundSet;
	}
}
