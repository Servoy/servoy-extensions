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
package com.servoy.extensions.beans.dbtreeview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.Serializable;

import javax.swing.border.Border;
import javax.swing.text.html.CSS;

import com.servoy.j2db.IApplication;
import com.servoy.j2db.IServoyBeanFactory2;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.IStyleRule;
import com.servoy.j2db.util.IStyleSheet;

/**
 * DB Tree View bean main class
 *
 * @author jblok
 */
@SuppressWarnings("nls")
public class DBTreeView implements IServoyBeanFactory2, Serializable, ITreeView
{
	private static final long serialVersionUID = 1L;
	public static final String ELEMENT_TYPE = "DBTREEVIEW";

	private String name;
	private Border border;
	private Color foreground;
	private Color background;
	private boolean opaque;
	private Font font;
	private Point location;
	private Dimension size;
	private String styleSheet;
	private String styleClass;

	public DBTreeView()
	{
	}

	public IComponent getBeanInstance(int servoy_application_type, IClientPluginAccess application, Object[] cargs)
	{
		IComponent t = null;
		if (servoy_application_type == IApplication.WEB_CLIENT || servoy_application_type == IApplication.HEADLESS_CLIENT)
		{
			t = getWicketDBTreeView(cargs, application);
		}
		else
		{
			t = getSwingDBTreeView(cargs, application);
		}
		styleSheet = (String)cargs[2];
		t.setName(name);
		applyStyleSheet(t, application.getStyleSheet(styleSheet), styleClass);
		if (font != null) t.setFont(font);
		if (background != null) t.setBackground(background);
		if (foreground != null) t.setForeground(foreground);
		if (border != null) t.setBorder(border);
		t.setOpaque(opaque);
		if (location != null) t.setLocation(location);
		if (size != null) t.setSize(size);
		return t;
	}

	@Override
	public Class< ? > getDocsClass()
	{
		return ScriptableDBTreeView.class;
	}

	protected IWicketTree getWicketDBTreeView(Object[] cargs, IClientPluginAccess application)
	{
		return new WicketDBTreeView(cargs[0].toString(), application);
	}

	protected SwingDBTreeView getSwingDBTreeView(Object[] cargs, IClientPluginAccess application)
	{
		SwingDBTreeView swingDBTreeView = new SwingDBTreeView(application);
		swingDBTreeView.setBorder(null);
		return swingDBTreeView;
	}

	public static Class[] getAllReturnedTypes()
	{
		return new Class[] { Binding.class, RelationInfo.class };
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Border getBorder()
	{
		return border;
	}

	public void setBorder(Border border)
	{
		this.border = border;
	}

	public Color getForeground()
	{
		return foreground;
	}

	public void setForeground(Color foreground)
	{
		this.foreground = foreground;
	}

	public Color getBackground()
	{
		return background;
	}

	public void setBackground(Color background)
	{
		this.background = background;
	}

	public boolean isOpaque()
	{
		return opaque;
	}

	public void setOpaque(boolean opaque)
	{
		this.opaque = opaque;
	}

	public Font getFont()
	{
		return font;
	}

	public void setFont(Font font)
	{
		this.font = font;
	}

	public Point getLocation()
	{
		return location;
	}

	public void setLocation(Point location)
	{
		this.location = location;
	}

	public Dimension getSize()
	{
		return size;
	}

	public void setSize(Dimension size)
	{
		this.size = size;
	}

	public void setBounds(Rectangle r)
	{
		setLocation(r.getLocation());
		setSize(r.getSize());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setStyleClass(java.lang.String)
	 */
	public void setStyleClass(String styleClass)
	{
		this.styleClass = styleClass;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getStyleClass()
	 */
	public String getStyleClass()
	{
		return styleClass;
	}

	public static void applyStyleSheet(IComponent component, IStyleSheet ss, String rule)
	{
		if (ss != null)
		{
			try
			{
				String treeViewRule = DBTreeView.ELEMENT_TYPE.toLowerCase();
				if (rule != null) treeViewRule = new StringBuffer(treeViewRule).append('.').append(rule).toString();
				IStyleRule style = ss.getCSSRule(treeViewRule);
				if (style != null)
				{
					if (style.getValue(CSS.Attribute.COLOR.toString()) != null)
					{
						Color cfg = ss.getForeground(style);
						if (cfg != null) component.setForeground(cfg);
					}
					Object sbackground_color = style.getValue(CSS.Attribute.BACKGROUND_COLOR.toString());
					if (sbackground_color != null)
					{
						if (IStyleSheet.COLOR_TRANSPARENT.equals(sbackground_color.toString()))
						{
							component.setOpaque(false);
						}
						else
						{
							Color cbg = ss.getBackground(style);
							if (cbg != null) component.setBackground(cbg);
						}
					}
					if (ss.hasFont(style))
					{
						Font f = ss.getFont(style);
						if (f != null) component.setFont(f);
					}
					if (ss.hasBorder(style))
					{
						Border b = ss.getBorder(style);
						if (b != null)
						{
							component.setBorder(b);
						}
					}
				}
			}
			catch (Exception e)
			{
				Debug.error(e);//parsing can fail in java 1.5
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#isTransparent()
	 */
	public boolean isTransparent()
	{
		return !isOpaque();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setTransparent(boolean)
	 */
	public void setTransparent(boolean transparent)
	{
		setOpaque(!transparent);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getBorderType()
	 */
	public Border getBorderType()
	{
		return getBorder();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setBorderType(javax.swing.border.Border)
	 */
	public void setBorderType(Border border)
	{
		setBorder(border);
	}
}
