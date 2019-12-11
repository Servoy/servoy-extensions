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
import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.List;

import javax.swing.AbstractCellEditor;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.mozilla.javascript.Function;

import com.servoy.extensions.beans.dbtreeview.FoundSetTreeModel.UserNode;
import com.servoy.j2db.Messages;
import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.IRecord;
import com.servoy.j2db.dataprocessing.ISwingFoundSet;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.ui.IComponent;
import com.servoy.j2db.util.DataSourceUtils;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.EnableScrollPanel;
import com.servoy.j2db.util.PersistHelper;
import com.servoy.j2db.util.UIUtils;
import com.servoy.j2db.util.Utils;

/**
 * Class representing the smart client db tree view
 *
 * @author gboros
 */
public class SwingDBTreeView extends EnableScrollPanel
	implements TreeSelectionListener, TreeExpansionListener, ITreeViewScriptMethods, IComponent, TableModelListener, ItemListener
{
	private static final long serialVersionUID = 1L;

	//the following attributes are transient so they are not stored when serialized in the repository
	protected transient SwingDBTree tree;
	private transient final FoundSetTreeModel model;

	protected BindingInfo bindingInfo;
	protected Binding defaultBinding = new Binding();
	private final boolean accessible = true;

	protected final IClientPluginAccess application;

	public SwingDBTreeView()
	{
		// only for scripting purposes
		application = null;
		model = null;
	}

	protected SwingDBTreeView(IClientPluginAccess application)
	{
		this.application = application;
		bindingInfo = new BindingInfo(application);

		setViewportView(tree = new SwingDBTree(this, application));

		tree.setEditable(true);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		UserNodeTreeCellRenderer unRenderer = new UserNodeTreeCellRenderer();
		tree.setCellEditor(new UserNodeTreeCellEditor());
		tree.setCellRenderer(unRenderer);

		tree.addTreeSelectionListener(this);
		tree.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger()) SwingDBTreeView.this.mouseRightClick(e);
				else SwingDBTreeView.this.mouseClicked(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger()) SwingDBTreeView.this.mouseRightClick(e);
			}

		});

		tree.addTreeWillExpandListener(new TreeWillExpandListener()
		{
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
			{
				// check all child items to detect dir/leaf status (need this for icon display)

				DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();

				if (expandedNode != null)
				{
					int childCount = expandedNode.getChildCount();

					try
					{
						for (int i = 0; i < childCount; i++)
						{
							((DefaultMutableTreeNode)expandedNode.getChildAt(i)).isLeaf();
						}
					}
					catch (Exception ex)
					{
						Debug.error(ex);
					}
				}

			}

			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException
			{
			}
		});


		tree.addTreeExpansionListener(this);

		model = new FoundSetTreeModel(application, bindingInfo, this);
		tree.setModel(model);

		//Enable tool tips.
		ToolTipManager.sharedInstance().registerComponent(tree);
	}

	public String getId()
	{
		return (String)getClientProperty("Id"); //$NON-NLS-1$
	}

	/*
	 * DBTreeView's JTree component
	 */
	public JTree getTree()
	{
		return tree;
	}

//	private boolean resizeNode = false;

//	public void paintComponent(Graphics g)
//	{
//
//		System.out.println("Swing tree : paintComponent : " + resizeNode);
//		js_refresh();
//		super.paintComponent(g);
//		if(!resizeNode)
//		{
//			resizeNode = true;
//			js_refresh();
//			resizeNode = false;
//		}
//	}

	@Deprecated
	public void js_setRoots(Object[] vargs)
	{
		IFoundSet fs = (IFoundSet)((vargs.length >= 1 && vargs[0] instanceof IFoundSet) ? vargs[0] : null);

		if (fs != null)
		{
			bindingInfo.removeRoots();
			bindingInfo.addRoots(fs);

			defaultBinding.setDataSource(fs.getDataSource());
			bindingInfo.addBinding(defaultBinding);
			defaultBinding.js_setTextDataprovider(((vargs.length >= 2 && vargs[1] != null) ? vargs[1].toString() : null));
			defaultBinding.js_setNRelationName(((vargs.length >= 3 && vargs[2] != null) ? vargs[2].toString() : defaultBinding.getNRelationName()));
			defaultBinding.js_setMRelationName(((vargs.length >= 4 && vargs[3] != null) ? vargs[3].toString() : defaultBinding.getMRelationName()));

			try
			{
				fs = ((IFoundSetInternal)fs).copy(false);
			}
			catch (Exception ex)
			{
				Debug.error(ex);
			}
		}

		js_refresh();
	}

	@Deprecated
	public void js_setCallBackInfo(Function methodToCallOnClick, String returndp)//can be related dp, when clicked and passed as argument to method
	{
		defaultBinding.js_setMethodToCallOnClick(methodToCallOnClick, returndp);
	}


	@Deprecated
	public void js_bindNodeTooltipTextDataProvider(String dp)//can be related dp
	{
		defaultBinding.js_setToolTipTextDataprovider(dp);
	}

	@Deprecated
	public void js_bindNodeChildSortDataProvider(String dp)//can be related dp
	{
		defaultBinding.js_setChildSortDataprovider(dp);
	}

	@Deprecated
	public void js_bindNodeFontTypeDataProvider(String dp)//can be related dp
	{
		defaultBinding.js_setFontTypeDataprovider(dp);
	}

	@Deprecated
	public void js_bindNodeImageURLDataProvider(String dp)//can be related dp
	{
		defaultBinding.js_setImageURLDataprovider(dp);
	}

	@Deprecated
	public void js_bindNodeImageMediaDataProvider(String dp)//can be related dp
	{
		defaultBinding.js_setImageMediaDataprovider(dp);
	}

	@Deprecated
	public void js_setNRelationName(String n_relationName)//normally self join
	{
		defaultBinding.js_setNRelationName(n_relationName);
	}

	@Deprecated
	public void js_setMRelationName(String m_relationName)//incase of n-m inbetween table
	{
		defaultBinding.js_setMRelationName(m_relationName);
	}


	/*
	 * name---------------------------------------------------
	 */
	public String js_getName()
	{
		return getName();
	}

	/*
	 * font---------------------------------------------------
	 */
	public void js_setFont(String font)
	{
		Font f = PersistHelper.createFont(font);
		setFont(f);
	}

	@Override
	public void setFont(Font f)
	{
		// if we have FontUIResource, create Font from it, else the default cell renderer will consider the font null
		Font font = f instanceof FontUIResource ? new Font(f.getName(), f.getStyle(), f.getSize()) : f;

		super.setFont(font);
		if (tree != null) tree.setFont(font);
	}


	/*
	 * bgcolor---------------------------------------------------
	 */
	public String js_getBgcolor()
	{
		return PersistHelper.createColorString(getBackground());
	}

	public void js_setBgcolor(String clr)
	{
		setBackground(PersistHelper.createColor(clr));
	}

	@Override
	public void setBackground(Color c)
	{
		super.setBackground(c);
		getViewport().setBackground(c);
		if (tree != null)
		{
			tree.setBackground(c);
		}
	}

	/*
	 * fgcolor---------------------------------------------------
	 */
	public String js_getFgcolor()
	{
		return PersistHelper.createColorString(getForeground());
	}

	public void js_setFgcolor(String clr)
	{
		setForeground(PersistHelper.createColor(clr));
	}

	@Override
	public void setForeground(Color c)
	{
		super.setForeground(c);
		getViewport().setForeground(c);
		if (tree != null)
		{
			tree.setForeground(c);
		}
	}

	/*
	 * visible---------------------------------------------------
	 */
	public void setComponentVisible(boolean visible)
	{
		setVisible(visible);
	}

	public boolean js_isVisible()
	{
		return isVisible();
	}

	public void js_setVisible(boolean b)
	{
		setVisible(b);
	}

	/*
	 * opaque---------------------------------------------------
	 */
	public boolean js_isTransparent()
	{
		return !isOpaque();
	}

	public void js_setTransparent(boolean b)
	{
		setOpaque(!b);
	}

	@Override
	public void setOpaque(boolean isOpaque)
	{
		super.setOpaque(isOpaque);
		getViewport().setOpaque(isOpaque);
		if (tree != null) tree.setOpaque(isOpaque);
	}

	/*
	 * enabled---------------------------------------------------
	 */
	public boolean js_isEnabled()
	{
		return isEnabled();
	}

	public void js_setEnabled(boolean b)
	{
		setEnabled(b);
	}

	public void setComponentEnabled(boolean enabled)
	{
		if (accessible)
		{
			setEnabled(enabled);
		}
	}

	/*
	 * tooltip---------------------------------------------------
	 */
	public void js_setToolTipText(String text)
	{
		if (text != null && text.startsWith("i18n:")) //$NON-NLS-1$
		{
			text = Messages.getString(text);
		}
		setToolTipText(text);
	}

	public String js_getToolTipText()
	{
		return getToolTipText();
	}

	/*
	 * location---------------------------------------------------
	 */
	public int js_getLocationX()
	{
		return getLocation().x;
	}

	public int js_getLocationY()
	{
		return getLocation().y;
	}

	public void js_setLocation(int x, int y)
	{
		setLocation(x, y);
	}

	/*
	 * size---------------------------------------------------
	 */
	public void js_setSize(int w, int h)
	{
		setSize(w, h);
	}

	public int js_getWidth()
	{
		return getSize().width;
	}

	public int js_getHeight()
	{
		return getSize().height;
	}

	/*
	 * jsmethods---------------------------------------------------
	 */
	public void js_setNodeLevelVisible(int level, boolean visible)
	{
		boolean didChange = false;
		int row = tree.getRowCount() - 1;
		while (row >= 0)
		{
			TreePath tp = tree.getPathForRow(row);
			if (tp.getPathCount() - 1 <= level && visible)
			{
				Object node = tp.getLastPathComponent();
				if (node instanceof UserNode) ((UserNode)node).loadChildren();
				if (((FoundSetTreeModel)tree.getModel()).hasChild(node) && tree.isCollapsed(row))
				{
					tree.expandRow(row);
					didChange = true;
				}
			}
			if (tp.getPathCount() - 1 >= level && !visible)
			{
				if (tree.isExpanded(row))
				{
					tree.collapseRow(row);
				}
			}
			row--;
		}
		if (didChange) js_setNodeLevelVisible(level, visible);//expanded or collapst rows are not included when looping, so must startover until there are no more changes
	}

	public Object[] js_getSelectionPath()
	{
		TreePath treePath = tree.getSelectionPath();

		return treePathToArray(treePath);
	}


	private Object[] treePathToArray(TreePath treePath)
	{
		if (treePath != null)
		{
			Object[] path = treePath.getPath();
			Object[] retval = new Object[path.length - 1];
			for (int i = 1; i < path.length; i++)
			{
				if (path[i] instanceof FoundSetTreeModel.UserNode)
				{
					FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)path[i];
					IRecord rec = un.getRecord();
					if (rec != null && rec.getPK() != null)
					{
						retval[i - 1] = rec.getPK()[0];
					}
				}
				else if (path[i] instanceof FoundSetTreeModel.RelationNode)
				{
					retval[i - 1] = new Integer(((FoundSetTreeModel.RelationNode)path[i]).getId());
				}
			}
			return retval;
		}
		else
		{
			return new Object[0];
		}
	}


	public void js_setSelectionPath(Object[] selectionPath)
	{
		List selection = createTreeNodePath(selectionPath);

		if (selection != null && selection.size() > 0)
		{
			selection.add(0, tree.getModel().getRoot());
			tree.setSelectionPath(new TreePath(selection.toArray()));
			int[] selectedRows = tree.getSelectionRows();
			if (selectedRows != null && selectedRows.length > 0) tree.scrollRowToVisible(selectedRows[0]);
		}
		else
		{
			tree.setSelectionPath(null);
		}
	}


	public void js_setExpandNode(Object[] nodePath, boolean expand_collapse)
	{
		List path = createTreeNodePath(nodePath);

		if (path != null && path.size() == nodePath.length)
		{
			path.add(0, tree.getModel().getRoot());

			TreePath treePath = new TreePath(path.toArray());
			if (expand_collapse)
			{
				tree.expandPath(treePath);
			}
			else
			{
				tree.collapsePath(treePath);
			}
		}
	}

	public boolean js_isNodeExpanded(Object[] nodePath)
	{
		List path = createTreeNodePath(nodePath);

		if (path != null && path.size() == nodePath.length)
		{
			path.add(0, tree.getModel().getRoot());

			TreePath treePath = new TreePath(path.toArray());

			return tree.isExpanded(treePath);
		}

		return false;
	}

	public void js_refresh()
	{
		synchronized (model)
		{
			FoundSetTreeModel foundSetTreeModel = ((FoundSetTreeModel)tree.getModel());

			ArrayList expandedPaths = new ArrayList();
			Enumeration expandedDescEnum = tree.getExpandedDescendants(new TreePath(foundSetTreeModel.getRoot()));

			if (expandedDescEnum != null)
			{
				while (expandedDescEnum.hasMoreElements())
				{
					expandedPaths.add(treePathToArray((TreePath)expandedDescEnum.nextElement()));
				}
			}

			Object[] selectionPath = js_getSelectionPath();

			foundSetTreeModel.resetRoot();
			foundSetTreeModel.nodeStructureChanged((DefaultMutableTreeNode)foundSetTreeModel.getRoot());

			List path;
			TreePath treePath;
			for (int i = 0; i < expandedPaths.size(); i++)
			{
				path = createTreeNodePath((Object[])expandedPaths.get(i));
				path.add(0, tree.getModel().getRoot());
				treePath = new TreePath(path.toArray());
				tree.expandPath(treePath);
			}

			js_setSelectionPath(selectionPath);

		}
	}

	public void js_setRowHeight(int height)
	{
		tree.setRowHeight(height);
	}

	public class UserNodeTreeCellEditor extends AbstractCellEditor implements TreeCellEditor
	{
		private static final long serialVersionUID = 1L;

		// custom node panel used for the renderer when using it for the editor
		private final JPanel nodePanel = new JPanel()
		{
			@Override
			public void setBounds(int x, int y, int width, int height)
			{
				super.setBounds(x, y, width, height);
				// make sure all components are in-place, because if it is used as editor
				// the click on it may miss the right child component
				doLayout();
			}
		};

		private final UserNodeTreeCellRenderer theEditor = new UserNodeTreeCellRenderer()
		{
			@Override
			protected JPanel getNodePanel()
			{
				return nodePanel;
			}
		};
		private int editingRow;

		public UserNodeTreeCellEditor()
		{
			theEditor.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					SwingDBTreeView.this.tree.setSelectionRow(editingRow);
					SwingDBTreeView.this.tree.stopEditing();
				}

			});
		}


		public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
		{
			Component treeCellEditor;

			this.editingRow = row;
			theEditor.getCheckBox().removeItemListener(SwingDBTreeView.this);
			treeCellEditor = theEditor.getTreeCellRendererComponent(tree, value, false, expanded, leaf, row, false);
			theEditor.getCheckBox().addItemListener(SwingDBTreeView.this);

			return treeCellEditor;
		}

		@Override
		public boolean isCellEditable(EventObject event)
		{
			boolean returnValue = false;

			if (event instanceof MouseEvent)
			{
				MouseEvent mouseEvent = (MouseEvent)event;

				TreePath path = SwingDBTreeView.this.tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());

				if (path != null)
				{
					Object node = path.getLastPathComponent();
					if ((node != null) && (node instanceof FoundSetTreeModel.UserNode))
					{
						returnValue = bindingInfo.hasCheckBox((FoundSetTreeModel.UserNode)node) &&
							bindingInfo.isCheckBoxEnabled((FoundSetTreeModel.UserNode)node);
					}
				}
			}

			return returnValue;
		}

		public Object getCellEditorValue()
		{
			return null;
		}


		@Override
		public boolean shouldSelectCell(EventObject anEvent)
		{
			return false;
		}
	}

	public class UserNodeTreeCellRenderer extends DefaultTreeCellRenderer
	{
		private static final long serialVersionUID = 1L;

		private final JPanel cellNodePanel = new JPanel();
		private final JCheckBox cb = new JCheckBox();
		private final JLabel img = new JLabel();

		private int rowFocus = -1;
		private boolean isRendering;

		public UserNodeTreeCellRenderer()
		{
			JPanel nodePanel = getNodePanel();
			nodePanel.setOpaque(false);
			nodePanel.setLayout(new BoxLayout(nodePanel, BoxLayout.X_AXIS));

			cb.setOpaque(false);
			img.setOpaque(false);

			Color clr = new Color(0, 0, 0, 0);
//			setBackground(clr);
			setBackgroundNonSelectionColor(clr);
		}

		protected JPanel getNodePanel()
		{
			return cellNodePanel;
		}

		public JCheckBox getCheckBox()
		{
			return cb;
		}

		public void setRowFocus(int rowFocus)
		{
			this.rowFocus = rowFocus;
		}

		public boolean isRendering()
		{
			return isRendering;
		}

		/**
		 * @see javax.swing.tree.TreeCellRenderer#getTreeCellRendererComponent(JTree, Object, boolean, boolean, boolean, int, boolean)
		 */
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			isRendering = true;
			JPanel nodePanel = getNodePanel();
			nodePanel.removeAll();

			JComponent comp = (JComponent)super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, (rowFocus == row) ? true : hasFocus);

			if (value instanceof FoundSetTreeModel.UserNode)
			{
				FoundSetTreeModel.UserNode _currentUserNode = (FoundSetTreeModel.UserNode)value;

				// Setting the tooltip text
				nodePanel.putClientProperty(JComponent.TOOL_TIP_TEXT_KEY, bindingInfo.getToolTipText(_currentUserNode));

				Font nodeFont = bindingInfo.getFont(_currentUserNode);
				if (nodeFont == null) nodeFont = SwingDBTreeView.this.getFont();
				setFont(nodeFont);

				int oldWidth = _currentUserNode.getWidth();
				setText(bindingInfo.getText(_currentUserNode));

				// Setting the icons for this usernode...
				Icon icon = bindingInfo.getIcon(_currentUserNode);
				if (icon != null)
				{
					if (isEnabled())
					{
						setIcon(icon);
					}
					else
					{
						setDisabledIcon(icon);
					}
				}
				else
				{
					setLeafIcon(getDefaultLeafIcon());
					setOpenIcon(getDefaultOpenIcon());
					setClosedIcon(getDefaultClosedIcon());
				}


				if (bindingInfo.hasCheckBox(_currentUserNode))
				{
					DefaultTreeCellRenderer compRenderer = (DefaultTreeCellRenderer)comp;
					img.setIcon(isEnabled() ? compRenderer.getIcon() : compRenderer.getDisabledIcon());
					nodePanel.add(img);
					compRenderer.setIcon(null);
					compRenderer.setDisabledIcon(null);
					cb.setSelected(bindingInfo.isCheckBoxChecked(_currentUserNode));
					cb.setEnabled(bindingInfo.isCheckBoxEnabled(_currentUserNode));
					nodePanel.add(cb);
				}

				_currentUserNode.setWidth((int)getPreferredSize().getWidth());
				if (oldWidth != _currentUserNode.getWidth())
				{
					try
					{
						((DefaultTreeModel)tree.getModel()).nodeChanged(_currentUserNode);
					}
					catch (Exception ex)
					{
						// ignore, an insert/update maybe changing the tree display
					}
				}

				_currentUserNode.addModificationListener();
			}
			else
			{
				setLeafIcon(getDefaultLeafIcon());
				setOpenIcon(getDefaultOpenIcon());
				setClosedIcon(getDefaultClosedIcon());
			}

			comp.setForeground(tree.getForeground());
			nodePanel.setBackground(tree.getBackground());
			if (selected)
			{
				comp.setForeground(super.getTextSelectionColor());
				nodePanel.setOpaque(false);
			}
			else
			{
				comp.setForeground(tree.getForeground());
				nodePanel.setOpaque(tree.isOpaque());
			}

			nodePanel.add(comp);
			nodePanel.invalidate();
			isRendering = false;

			return nodePanel;
		}
	}

	protected Timer clickTimer;

	/*
	 * Tree node mouse click listener
	 */
	protected void mouseClicked(final MouseEvent e)
	{
		TreePath selectedPath = tree.getPathForLocation(e.getX(), e.getY());
		if (selectedPath != null)
		{
			final DefaultMutableTreeNode tn = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
			IRuntimeWindow runtimeWindow = application.getCurrentRuntimeWindow();
			Point windowLocation = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) windowLocation = ((ISmartRuntimeWindow)runtimeWindow).getWindow().getLocationOnScreen();
			Point treeLocation = tree.getLocationOnScreen();
			final Point treeLocationToWindow = new Point((int)(treeLocation.getX() - windowLocation.getX()),
				(int)(treeLocation.getY() - windowLocation.getY()));

			if (e.getClickCount() == 1)
			{
				clickTimer = new Timer(UIUtils.getClickInterval(), new ActionListener()
				{
					public void actionPerformed(ActionEvent ev)
					{
						callMethodOnClick(tn, treeLocationToWindow.x + e.getX(), treeLocationToWindow.y + e.getY(), null);
					}
				});
				clickTimer.setRepeats(false);
				clickTimer.start();
			}
			else if (e.getClickCount() == 2)
			{
				clickTimer.stop();
				callMethod(tn);
				callMethodOnDoubleClick(tn, treeLocationToWindow.x + e.getX(), treeLocationToWindow.y + e.getY(), null);
			}
		}
	}

	protected void mouseRightClick(MouseEvent e)
	{
		TreePath selectedPath = tree.getPathForLocation(e.getX(), e.getY());
		if (selectedPath != null)
		{
			DefaultMutableTreeNode tn = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
			IRuntimeWindow runtimeWindow = application.getCurrentRuntimeWindow();
			Point windowLocation = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) windowLocation = ((ISmartRuntimeWindow)runtimeWindow).getWindow().getLocationOnScreen();
			Point treeLocation = tree.getLocationOnScreen();
			Point treeLocationToWindow = new Point((int)(treeLocation.getX() - windowLocation.getX()), (int)(treeLocation.getY() - windowLocation.getY()));
			callMethodOnRightClick(tn, treeLocationToWindow.x + e.getX(), treeLocationToWindow.y + e.getY(), null);
		}
	}

	public void treeCollapsed(TreeExpansionEvent event)
	{
	}

	public void treeExpanded(TreeExpansionEvent event)
	{
	}

	/*
	 * Tree node selection listener
	 *
	 * @see javax.swing.event.TreeSelectionListener#valueChanged(javax.swing.event.TreeSelectionEvent)
	 */
	public void valueChanged(TreeSelectionEvent e)
	{
		if (tree.getSelectionCount() >= 1)
		{
			DefaultMutableTreeNode tn = (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
			callMethod(tn);
		}
	}

	protected void callMethod(DefaultMutableTreeNode tn)
	{
		if (tn instanceof FoundSetTreeModel.UserNode)
		{
			FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)tn;
			IRecord r = un.getRecord();
			if (r != null)
			{
				String returnProvider = bindingInfo.getReturnDataprovider(un);

				String[] server_table = DataSourceUtils.getDBServernameTablename(un.getFoundSet().getDataSource());
				Object[] args = new Object[] { r.getValue(returnProvider), (server_table == null ? null : server_table[1]) };

				FunctionDefinition f = bindingInfo.getCallBack((FoundSetTreeModel.UserNode)tn);
				if (f != null)
				{
					f.executeAsync(application, args);
				}
			}
		}
	}

	protected void callMethodOnCheckBoxChange(DefaultMutableTreeNode tn, int state)
	{
		if (tn instanceof FoundSetTreeModel.UserNode)
		{
			FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)tn;

			bindingInfo.setCheckBox(un, state == ItemEvent.SELECTED);

			IRecord r = un.getRecord();
			if (r != null)
			{
				String returnProvider = bindingInfo.getReturnDataproviderOnCheckBoxChange(un);

				String[] server_table = DataSourceUtils.getDBServernameTablename(un.getFoundSet().getDataSource());
				Object[] args = new Object[] { r.getValue(returnProvider), (server_table == null ? null : server_table[1]) };

				FunctionDefinition f = bindingInfo.getMethodToCallOnCheckBoxChange((FoundSetTreeModel.UserNode)tn);
				if (f != null)
				{
					f.executeAsync(application, args);
				}
			}
		}
	}

	protected void callMethodOnRightClick(DefaultMutableTreeNode tn, int x, int y, Object arg)
	{
		if (tn instanceof FoundSetTreeModel.UserNode)
		{
			FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)tn;
			IRecord r = un.getRecord();
			if (r != null)
			{
				String returnProvider = bindingInfo.getReturnDataproviderOnRightClick(un);

				String[] server_table = DataSourceUtils.getDBServernameTablename(un.getFoundSet().getDataSource());
				Object[] args = new Object[] { r.getValue(returnProvider), (server_table == null ? null : server_table[1]), Integer.valueOf(x), Integer.valueOf(
					y), arg };

				FunctionDefinition f = bindingInfo.getMethodToCallOnRightClick((FoundSetTreeModel.UserNode)tn);
				if (f != null)
				{
					f.execute(application, args, true);
				}
			}
		}
	}

	protected void callMethodOnDoubleClick(DefaultMutableTreeNode tn, int x, int y, Object arg)
	{
		if (tn instanceof FoundSetTreeModel.UserNode)
		{
			FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)tn;
			IRecord r = un.getRecord();
			if (r != null)
			{
				String returnProvider = bindingInfo.getReturnDataproviderOnDoubleClick(un);

				String[] server_table = DataSourceUtils.getDBServernameTablename(un.getFoundSet().getDataSource());
				Object[] args = new Object[] { r.getValue(returnProvider), (server_table == null ? null : server_table[1]), Integer.valueOf(x), Integer.valueOf(
					y), arg };

				FunctionDefinition f = bindingInfo.getMethodToCallOnDoubleClick((FoundSetTreeModel.UserNode)tn);
				if (f != null)
				{
					f.execute(application, args, true);
				}
			}
		}
	}

	protected void callMethodOnClick(DefaultMutableTreeNode tn, int x, int y, Object arg)
	{
		if (tn instanceof FoundSetTreeModel.UserNode)
		{
			FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)tn;
			IRecord r = un.getRecord();
			if (r != null)
			{
				String returnProvider = bindingInfo.getReturnDataproviderOnClick(un);

				String[] server_table = DataSourceUtils.getDBServernameTablename(un.getFoundSet().getDataSource());
				Object[] args = new Object[] { r.getValue(returnProvider), (server_table == null ? null : server_table[1]), Integer.valueOf(x), Integer.valueOf(
					y), arg };

				FunctionDefinition f = bindingInfo.getMethodToCallOnClick((FoundSetTreeModel.UserNode)tn);
				if (f != null)
				{
					f.execute(application, args, true);
				}
			}
		}
	}

	/**
	 * Create list of TreeNode items from pk ids
	 *
	 * @param nodePath pk ids
	 * @return TreeNode items
	 */
	private List createTreeNodePath(Object[] nodePath)
	{
		List path = null;

		if (nodePath != null)
		{
			path = new ArrayList();

			DefaultMutableTreeNode lastNode = ((DefaultMutableTreeNode)tree.getModel().getRoot());
			for (Object pk : nodePath)
			{
				boolean found = false;
				for (int j = 0; j < lastNode.getChildCount(); j++)
				{
					DefaultMutableTreeNode child = (DefaultMutableTreeNode)lastNode.getChildAt(j);
					if (child instanceof FoundSetTreeModel.UserNode)
					{
						FoundSetTreeModel.UserNode un = (FoundSetTreeModel.UserNode)child;
						IRecord rec = un.getRecord();
						if (rec != null && rec.getPK() != null)
						{
							if (Utils.equalObjects(rec.getPK()[0], pk))
							{
								path.add(child);
								lastNode = child;
								found = true;
								if (lastNode.getChildCount() == 1)
								{
									model.lazyLoadChilderenIfNeeded(lastNode);
								}
								break;
							}
						}
					}
					else if (child instanceof FoundSetTreeModel.RelationNode)
					{
						FoundSetTreeModel.RelationNode un = (FoundSetTreeModel.RelationNode)child;
						if (new Integer(un.getId()).equals(pk))
						{
							path.add(child);
							lastNode = child;
							found = true;
							if (lastNode.getChildCount() == 1)
							{
								model.lazyLoadChilderenIfNeeded(lastNode);
							}
							break;
						}
					}
				}
				if (!found) break;
			}
		}

		return path;
	}

	public Class[] getAllReturnedTypes()
	{
		return DBTreeView.getAllReturnedTypes();
	}

	public void tableChanged(TableModelEvent e)
	{
		Object foundSet = e.getSource();

		if (foundSet instanceof ISwingFoundSet && model != null && !((UserNodeTreeCellRenderer)tree.getCellRenderer()).isRendering())
		{
			int changeType = e.getType();
			DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
			int firstRow = e.getFirstRow();
			int lastRow = e.getLastRow();

			switch (changeType)
			{
				case TableModelEvent.INSERT :
				case TableModelEvent.DELETE :
					js_refresh();
					break;
				case TableModelEvent.UPDATE :
					synchronized (model)
					{
						DefaultMutableTreeNode updateUserNode;
						for (int i = firstRow; i <= lastRow; i++)
						{
							updateUserNode = model.findNode(root, (ISwingFoundSet)foundSet, i);
							if (updateUserNode != null)
							{
								try
								{
									model.nodeChanged(updateUserNode);
								}
								catch (Exception ex)
								{
									// ignore, an insert/update maybe changing the tree display
								}
							}
						}
					}
					break;
			}
		}
	}

	/*
	 * Tree node checkbox state listener
	 *
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	public void itemStateChanged(ItemEvent e)
	{
		TreePath editingPath = tree.getEditingPath();

		if (editingPath != null)
		{
			DefaultMutableTreeNode tn = (DefaultMutableTreeNode)editingPath.getLastPathComponent();
			tree.stopEditing();
			int state = e.getStateChange();
			callMethodOnCheckBoxChange(tn, state);
		}
	}

	public Binding js_createBinding(String datasource)
	{
		Binding binding = new Binding();
		binding.setDataSource(datasource);
		bindingInfo.addBinding(binding);
		return binding;
	}

	public Binding js_createBinding(String serverName, String tableName)
	{
		Binding binding = new Binding();
		binding.setServerName(serverName);
		binding.setTableName(tableName);
		bindingInfo.addBinding(binding);
		return binding;
	}

//	public void js_activateBinding(Binding binding)
//	{
//		// TODO Auto-generated method stub
//	}
//
//	public void js_removeBinding(Binding binding)
//	{
//		// TODO Auto-generated method stub
//	}

	public int js_addRoots(Object foundSet)
	{
		int addedRootNodes = 0;
		if (foundSet instanceof IFoundSet)
		{
			bindingInfo.addRoots((IFoundSet)foundSet);
			js_refresh();
			addedRootNodes = ((IFoundSet)foundSet).getSize();
		}
		return addedRootNodes;
	}

	public void js_removeAllRoots()
	{
		bindingInfo.removeRoots();
	}

	public RelationInfo js_createRelationInfo()
	{
		return new RelationInfo();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDrag(org.mozilla.javascript.Function)
	 */
	public void js_setOnDrag(Function fOnDrag)
	{
		tree.setOnDragCallback(fOnDrag);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDragEnd(org.mozilla.javascript.Function)
	 */
	public void js_setOnDragEnd(Function fOnDragEnd)
	{
		tree.setOnDragEndCallback(fOnDragEnd);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDragOver(org.mozilla.javascript.Function)
	 */
	public void js_setOnDragOver(Function fOnDragOver)
	{
		tree.setOnDragOverCallback(fOnDragOver);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeViewScriptMethods#js_setOnDrop(org.mozilla.javascript.Function)
	 */
	public void js_setOnDrop(Function fOnDrop)
	{
		tree.setOnDropCallback(fOnDrop);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#setStyleClass(java.lang.String)
	 */
	public void setStyleClass(String styleClass)
	{
		// ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.extensions.beans.dbtreeview.ITreeView#getStyleClass()
	 */
	public String getStyleClass()
	{
		return null;
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