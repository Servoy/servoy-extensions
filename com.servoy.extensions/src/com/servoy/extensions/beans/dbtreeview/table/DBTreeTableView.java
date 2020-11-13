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
package com.servoy.extensions.beans.dbtreeview.table;


import java.util.ArrayList;
import java.util.Hashtable;

import com.servoy.extensions.beans.dbtreeview.Binding;
import com.servoy.extensions.beans.dbtreeview.DBTreeView;
import com.servoy.extensions.beans.dbtreeview.IWicketTree;
import com.servoy.extensions.beans.dbtreeview.RelationInfo;
import com.servoy.extensions.beans.dbtreeview.SwingDBTreeView;
import com.servoy.j2db.plugins.IClientPluginAccess;

/**
 * DB Tree Table View bean main class
 *
 * @author gboros
 */
public class DBTreeTableView extends DBTreeView
{
	private static final long serialVersionUID = 1L;

	private String treeColumnHeader;
	private final ArrayList<Column> columns = new ArrayList<Column>();

	@Override
	protected SwingDBTreeView getSwingDBTreeView(Object[] cargs, IClientPluginAccess application)
	{
		return new SwingDBTreeTableView(application, this);
	}

	@Override
	protected IWicketTree getWicketDBTreeView(Object[] cargs, IClientPluginAccess application)
	{
		return new InmethodDBTreeTableView(cargs[0].toString(), application, new ArrayList(), this);
	}

	@Override
	public Class< ? > getDocsClass()
	{
		return ScriptableDBTreeTableView.class;
	}

	public static Class[] getAllReturnedTypes()
	{
		return new Class[] { Binding.class, Column.class, RelationInfo.class };
	}

	public void addColumn(Column column)
	{
		columns.add(column);
	}

	ArrayList<ArrayList<Column>> groupedColumns = new ArrayList<ArrayList<Column>>();

	public ArrayList<ArrayList<Column>> getColumns()
	{
		if (columnsChanged)
		{
			groupedColumns.clear();
			Hashtable<String, Integer> columnPosition = new Hashtable<String, Integer>();
			Column column;
			for (int i = 0; i < columns.size(); i++)
			{
				column = columns.get(i);
				if (columnPosition.containsKey(column.getHeader()))
				{
					int columnIdx = columnPosition.get(column.getHeader()).intValue();
					groupedColumns.get(columnIdx).add(column);

				}
				else
				{
					ArrayList<Column> sameHeaderColumns = new ArrayList<Column>();
					sameHeaderColumns.add(column);
					groupedColumns.add(sameHeaderColumns);
					columnPosition.put(column.getHeader(), new Integer(groupedColumns.size() - 1));
				}
			}
			columnsChanged = false;
		}
		return groupedColumns;
	}

	public void removeAllColumns()
	{
		columns.clear();
	}

	public void setTreeColumnHeader(String treeColumnHeader)
	{
		this.treeColumnHeader = treeColumnHeader;
	}

	public String getTreeColumnHeader()
	{
		return this.treeColumnHeader == null ? "" : this.treeColumnHeader;
	}

	private boolean columnsChanged;

	public void flagColumnsChanged()
	{
		columnsChanged = true;
	}

	private int treeColumnPreferredWidth;

	public void setTreeColumnPreferredWidth(int preferredWidth)
	{
		this.treeColumnPreferredWidth = preferredWidth;
	}

	public int getTreeColumnPreferredWidth()
	{
		return treeColumnPreferredWidth;
	}
}
