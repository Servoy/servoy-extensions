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

package com.servoy.extensions.beans.dbtreeview.table;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;

import javax.swing.border.Border;

import org.mozilla.javascript.Function;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.extensions.beans.dbtreeview.Binding;
import com.servoy.extensions.beans.dbtreeview.DBTreeView;
import com.servoy.extensions.beans.dbtreeview.RelationInfo;
import com.servoy.j2db.documentation.ServoyDocumented;

/**
 * @author lvostinar
 *
 */
@ServoyClientSupport(ng = false, wc = true, sc = true)
@ServoyDocumented(category = ServoyDocumented.BEANS, publicName = "DB Tree Table View", extendsComponent = "DB Tree View")
public class ScriptableDBTreeTableView implements ITreeTableScriptMethods
{

	/**
	 *
	 */
	public ScriptableDBTreeTableView()
	{
		// TODO Auto-generated constructor stub
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getBgcolor()
	 */
	@Override
	public String js_getBgcolor()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setBgcolor(java.lang.String)
	 */
	@Override
	public void js_setBgcolor(String bg)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getFgcolor()
	 */
	@Override
	public String js_getFgcolor()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setFgcolor(java.lang.String)
	 */
	@Override
	public void js_setFgcolor(String fg)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getLocationX()
	 */
	@Override
	public int js_getLocationX()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getLocationY()
	 */
	@Override
	public int js_getLocationY()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setLocation(int, int)
	 */
	@Override
	public void js_setLocation(int x, int y)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getWidth()
	 */
	@Override
	public int js_getWidth()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getHeight()
	 */
	@Override
	public int js_getHeight()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setSize(int, int)
	 */
	@Override
	public void js_setSize(int w, int h)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getName()
	 */
	@Override
	public String js_getName()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getSelectionPath()
	 */
	@Override
	public Object[] js_getSelectionPath()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setSelectionPath(java.lang.Object[])
	 */
	@Override
	public void js_setSelectionPath(Object[] path)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_getToolTipText()
	 */
	@Override
	public String js_getToolTipText()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setToolTipText(java.lang.String)
	 */
	@Override
	public void js_setToolTipText(String tip)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_isEnabled()
	 */
	@Override
	public boolean js_isEnabled()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setEnabled(boolean)
	 */
	@Override
	public void js_setEnabled(boolean enabled)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_isTransparent()
	 */
	@Override
	public boolean js_isTransparent()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setTransparent(boolean)
	 */
	@Override
	public void js_setTransparent(boolean transparent)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_isVisible()
	 */
	@Override
	public boolean js_isVisible()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setVisible(boolean)
	 */
	@Override
	public void js_setVisible(boolean visible)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setFont(java.lang.String)
	 */
	@Override
	public void js_setFont(String font)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setExpandNode(java.lang.Object[], boolean)
	 */
	@Override
	public void js_setExpandNode(Object[] nodePath, boolean expand_collapse)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_isNodeExpanded(java.lang.Object[])
	 */
	@Override
	public boolean js_isNodeExpanded(Object[] nodePath)
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setNodeLevelVisible(int, boolean)
	 */
	@Override
	public void js_setNodeLevelVisible(int level, boolean visible)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_refresh()
	 */
	@Override
	public void js_refresh()
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_addRoots(java.lang.Object)
	 */
	@Override
	public int js_addRoots(Object foundSet)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_removeAllRoots()
	 */
	@Override
	public void js_removeAllRoots()
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_createBinding(java.lang.String)
	 */
	@Override
	public Binding js_createBinding(String datasource)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_createBinding(java.lang.String, java.lang.String)
	 */
	@Override
	public Binding js_createBinding(String serverName, String tableName)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_createRelationInfo()
	 */
	@Override
	public RelationInfo js_createRelationInfo()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setRowHeight(int)
	 */
	@Override
	public void js_setRowHeight(int height)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDrag(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDrag(Function fOnDrag)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDragEnd(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDragEnd(Function fOnDragEnd)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDragOver(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDragOver(Function fOnDragOver)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDrop(org.mozilla.javascript.Function)
	 */
	@Override
	public void js_setOnDrop(Function fOnDrop)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setRoots(java.lang.Object[])
	 */
	@Override
	public void js_setRoots(Object[] vargs)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setMRelationName(java.lang.String)
	 */
	@Override
	public void js_setMRelationName(String name)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setNRelationName(java.lang.String)
	 */
	@Override
	public void js_setNRelationName(String name)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_bindNodeFontTypeDataProvider(java.lang.String)
	 */
	@Override
	public void js_bindNodeFontTypeDataProvider(String dp)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_bindNodeImageMediaDataProvider(java.lang.String)
	 */
	@Override
	public void js_bindNodeImageMediaDataProvider(String dp)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_bindNodeImageURLDataProvider(java.lang.String)
	 */
	@Override
	public void js_bindNodeImageURLDataProvider(String dp)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_bindNodeTooltipTextDataProvider(java.lang.String)
	 */
	@Override
	public void js_bindNodeTooltipTextDataProvider(String dp)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setCallBackInfo(org.mozilla.javascript.Function, java.lang.String)
	 */
	@Override
	public void js_setCallBackInfo(Function f, String returndp)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_bindNodeChildSortDataProvider(java.lang.String)
	 */
	@Override
	public void js_bindNodeChildSortDataProvider(String dp)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public Class< ? >[] getAllReturnedTypes()
	{
		return DBTreeView.getAllReturnedTypes();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getBorder()
	 */
	@Override
	public Border getBorder()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setBorder(javax.swing.border.Border)
	 */
	@Override
	public void setBorder(Border border)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getForeground()
	 */
	@Override
	public Color getForeground()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setForeground(java.awt.Color)
	 */
	@Override
	public void setForeground(Color foreground)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getBackground()
	 */
	@Override
	public Color getBackground()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setBackground(java.awt.Color)
	 */
	@Override
	public void setBackground(Color background)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#isOpaque()
	 */
	@Override
	public boolean isOpaque()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setOpaque(boolean)
	 */
	@Override
	public void setOpaque(boolean opaque)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getFont()
	 */
	@Override
	public Font getFont()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setFont(java.awt.Font)
	 */
	@Override
	public void setFont(Font font)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getLocation()
	 */
	@Override
	public Point getLocation()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setLocation(java.awt.Point)
	 */
	@Override
	public void setLocation(Point location)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getSize()
	 */
	@Override
	public Dimension getSize()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setSize(java.awt.Dimension)
	 */
	@Override
	public void setSize(Dimension size)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setStyleClass(java.lang.String)
	 */
	@Override
	public void setStyleClass(String styleClass)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getStyleClass()
	 */
	@Override
	public String getStyleClass()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#isTransparent()
	 */
	@Override
	public boolean isTransparent()
	{
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setTransparent(boolean)
	 */
	@Override
	public void setTransparent(boolean transparent)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getBorderType()
	 */
	@Override
	public Border getBorderType()
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setBorderType(javax.swing.border.Border)
	 */
	@Override
	public void setBorderType(Border border)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_setTreeColumnHeader(java.lang.String)
	 */
	@Override
	public void js_setTreeColumnHeader(String treeColumnHeader)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_setTreeColumnPreferredWidth(int)
	 */
	@Override
	public void js_setTreeColumnPreferredWidth(int preferredWidth)
	{
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_createColumn(java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Column js_createColumn(String servername, String tablename, String header, String fieldname)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_createColumn(java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String, int)
	 */
	@Override
	public Column js_createColumn(String servername, String tablename, String header, String fieldname, int preferredWidth)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_createColumn(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Column js_createColumn(String datasource, String header, String fieldname)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_createColumn(java.lang.String, java.lang.String, java.lang.String, int)
	 */
	@Override
	public Column js_createColumn(String datasource, String header, String fieldname, int preferredWidth)
	{
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.table.ITreeTableScriptMethods#js_removeAllColumns()
	 */
	@Override
	public void js_removeAllColumns()
	{
		// TODO Auto-generated method stub

	}

}
