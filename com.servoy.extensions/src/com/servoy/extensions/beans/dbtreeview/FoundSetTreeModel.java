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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import com.servoy.j2db.dataprocessing.IFoundSet;
import com.servoy.j2db.dataprocessing.IFoundSetInternal;
import com.servoy.j2db.dataprocessing.IModificationListener;
import com.servoy.j2db.dataprocessing.IRecord;
import com.servoy.j2db.dataprocessing.ISwingFoundSet;
import com.servoy.j2db.dataprocessing.ModificationEvent;
import com.servoy.j2db.dataprocessing.SortColumn;
import com.servoy.j2db.persistence.IColumn;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.util.Debug;

/**
 * Data model based on foundset(s) used by the tree
 *
 * @author gboros
 */
public class FoundSetTreeModel extends DefaultTreeModel
{
	private static final long serialVersionUID = 1L;

	private final IClientPluginAccess application;
	private final BindingInfo bindingInfo;

	private final TableModelListener tableModelListener;
	private final ArrayList nodeFoundSets = new ArrayList();

	public FoundSetTreeModel(IClientPluginAccess application, BindingInfo bindingInfo, TableModelListener tableModelListener)
	{
		super(new DefaultMutableTreeNode(), false);

		this.application = application;
		this.tableModelListener = tableModelListener;
		this.bindingInfo = bindingInfo;

		setRoot((TreeNode)(new JTree().getModel().getRoot()));

//		resetRoot();
	}


	public synchronized void resetRoot()
	{
		if (tableModelListener != null)
		{
			int nodeFoundSetsSize = nodeFoundSets.size();
			for (int i = 0; i < nodeFoundSetsSize; i++)
			{
				if (nodeFoundSets.get(i) instanceof ISwingFoundSet)
				{
					((ISwingFoundSet)nodeFoundSets.get(i)).removeTableModelListener(tableModelListener);
				}
			}
		}

		nodeFoundSets.clear();

		DefaultMutableTreeNode root = (DefaultMutableTreeNode)getRoot();
		removeModificationListenerRecursiv(root); // remove all nodes modification listeners
		root.removeAllChildren();

		DefaultMutableTreeNode child;

		ArrayList rootFoundSets = bindingInfo.getRootFoundSets();
		IFoundSet rootFs;
		for (int j = 0; j < rootFoundSets.size(); j++)
		{
			rootFs = (IFoundSet)rootFoundSets.get(j);
			for (int i = 0; i < rootFs.getSize(); i++)
			{
				rootFs.getRecord(i);
				child = createChildNode(root, rootFs, i);
			}
			//check child item to detect dir/leaf status (need this for icon display)
			Enumeration<TreeNode> childEnum = root.children();
			while (childEnum.hasMoreElements())
				childEnum.nextElement().isLeaf();
			addListenerToFoundSet(rootFs);
		}

	}


	private void addListenerToFoundSet(IFoundSet foundSet)
	{
		if (foundSet != null && foundSet instanceof ISwingFoundSet && tableModelListener != null)
		{
			if (nodeFoundSets.indexOf(foundSet) == -1)
			{
				((ISwingFoundSet)foundSet).addTableModelListener(tableModelListener);
				nodeFoundSets.add(foundSet);
			}
		}
	}

	private void removeModificationListenerRecursiv(DefaultMutableTreeNode node)
	{
		int childCount = 0;
		if (node instanceof UserNode)
		{
			((UserNode)node).removeModificationListener();
			if (((UserNode)node).isInitialized()) childCount = node.getChildCount();
		}
		else childCount = node.getChildCount();

		if (childCount > 0)
		{
			for (int i = 0; i < childCount; i++)
			{
				removeModificationListenerRecursiv((DefaultMutableTreeNode)node.getChildAt(i));
			}
		}
	}

	private DefaultMutableTreeNode createChildNode(DefaultMutableTreeNode node, IFoundSet foundSet, int recordIndex)
	{
		UserNode child = new UserNode(foundSet, recordIndex);
		node.add(child);
		return child;
	}

	public boolean hasChild(Object obj)
	{
		if (obj instanceof UserNode)
		{
			UserNode un = (UserNode)obj;
			// check if we have multiple child relations
			RelationInfo[] relationInfos = bindingInfo.getNRelationInfos(un);
			if (relationInfos != null && relationInfos.length > 0)
			{
				return true;
			}

			IRecord rec = un.getRecord();
			if (rec != null)
			{
				IFoundSet nfs;
				String n_relationName = bindingInfo.getNRelationName(un);
				String m_relationName = bindingInfo.getMRelationName(un);

				nfs = rec.getRelatedFoundSet(n_relationName);

				if (nfs != null && nfs.getSize() > 0)
				{
					if (m_relationName == null)
					{
						return true;
					}
					else
					{
						for (int ii = 0; ii < nfs.getSize(); ii++)
						{
							IRecord rel = nfs.getRecord(ii);
							IFoundSet mfs = rel.getRelatedFoundSet(m_relationName);
							if (mfs != null)
							{
								if (mfs.getSize() > 0) return true;
							}
						}
					}
				}
			}
		}
		else if (obj instanceof RelationNode)
		{
			RelationNode un = (RelationNode)obj;

			UserNode parentUserNode = (UserNode)un.getParent();
			IRecord parentRecord = parentUserNode.getRecord();

			IFoundSet nfs = parentRecord.getRelatedFoundSet(((RelationInfo)un.getUserObject()).getNRelationName());

			if (nfs != null && nfs.getSize() > 0)
			{
				return true;
			}
		}

		return false;
	}

	public synchronized void lazyLoadChilderenIfNeeded(Object obj)
	{
		internal_lazyLoadChilderenIfNeeded(obj);
		if (obj instanceof UserNode)
		{
			UserNode un = (UserNode)obj;
			if (un.getChildCount() > 0)
			{
				DefaultMutableTreeNode child = (DefaultMutableTreeNode)un.children().nextElement();
				if (child instanceof UserNode) addListenerToFoundSet(((UserNode)child).getFoundSet());
			}
		}
		else if (obj instanceof RelationNode)
		{
			RelationNode rn = (RelationNode)obj;
			if (rn.getChildCount() > 0) addListenerToFoundSet(((UserNode)rn.children().nextElement()).getFoundSet());
		}
	}

	//lazy load
	private void internal_lazyLoadChilderenIfNeeded(Object obj)
	{
		if (obj instanceof UserNode)
		{
			UserNode un = (UserNode)obj;
			if (!un.isInitialized())
			{
				un.flagInitialized();

				// check if we have multiple child relations
				RelationInfo[] relationInfos = bindingInfo.getNRelationInfos(un);
				if (relationInfos != null && relationInfos.length > 0)
				{
					int id = 0;
					for (RelationInfo relationInfo : relationInfos)
						un.add(new RelationNode(id++, relationInfo));
					return;
				}

				IRecord rec = un.getRecord();
				if (rec != null)
				{
					IFoundSet nfs;
					String n_relationName = bindingInfo.getNRelationName(un);
					String m_relationName = bindingInfo.getMRelationName(un);


					nfs = rec.getRelatedFoundSet(n_relationName);
					if (nfs != null && nfs.getSize() > 0)
					{
						if (m_relationName == null)
						{
							List sortColumns = getSortColumns((Table)((IFoundSetInternal)nfs).getTable(), bindingInfo.getChildSortDataprovider(un));
							if (sortColumns != null)
							{
								try
								{
									nfs.sort(sortColumns);
								}
								catch (Exception ex)
								{
									Debug.error(ex);
								}
							}
							for (int i = 0; i < nfs.getSize(); i++)
							{
								nfs.getRecord(i); // to force next record loading
								createChildNode(un, nfs, i);
							}
						}
						else
						{
							ArrayList mfsRecordsSortedIdx = new ArrayList();
							ArrayList mfsSorted = new ArrayList();

							int sortOrder = SortColumn.ASCENDING;

							for (int ii = 0; ii < nfs.getSize(); ii++)
							{
								IRecord rel = nfs.getRecord(ii);
								IFoundSet mfs = rel.getRelatedFoundSet(m_relationName);
								if (mfs != null)
								{
									for (int i = 0; i < mfs.getSize(); i++)
									{
										IRecord mfsRec = mfs.getRecord(i);

										// if sorting add mfs value o right position
										List sortColumns = getSortColumns((Table)((IFoundSetInternal)mfs).getTable(), bindingInfo.getChildSortDataprovider(un));
										if (sortColumns != null)
										{
											SortColumn sortCol = (SortColumn)sortColumns.get(0);
											String sortDataProvider = sortCol.getDataProviderID();
											sortOrder = sortCol.getSortOrder();
											if (mfsRec != null)
											{
												if (mfsRec.getValue(sortDataProvider) != null && mfsRec.getValue(sortDataProvider) instanceof Comparable)
												{
													Comparable sMfsRecValue = (Comparable)mfsRec.getValue(sortDataProvider);
													Comparable sSortedValue;
													int n;
													for (n = 0; n < mfsRecordsSortedIdx.size(); n++)
													{
														int nSortedRecIdx = ((Integer)mfsRecordsSortedIdx.get(n)).intValue();
														sSortedValue = (Comparable)((IFoundSet)mfsSorted.get(n)).getRecord(nSortedRecIdx).getValue(
															sortDataProvider);

														if (sSortedValue == null) continue;

														if (sMfsRecValue.getClass().getName().equals(sSortedValue.getClass().getName()) &&
															sMfsRecValue.compareTo(sSortedValue) < 0)
														{
															break;
														}
													}

													mfsRecordsSortedIdx.add(n, new Integer(i));
													mfsSorted.add(n, mfs);
												}
												else
												{
													mfsRecordsSortedIdx.add(0, new Integer(i));
													mfsSorted.add(0, mfs);
												}
											}
										}
										else
										{
											mfsRecordsSortedIdx.add(new Integer(i));
											mfsSorted.add(mfs);
										}
									}
								}
							}

							// should be done better
							if (sortOrder == SortColumn.ASCENDING)
							{
								for (int i = 0; i < mfsRecordsSortedIdx.size(); i++)
								{
									createChildNode((DefaultMutableTreeNode)un, (IFoundSet)mfsSorted.get(i), ((Integer)mfsRecordsSortedIdx.get(i)).intValue());
								}
							}
							else
							{
								for (int i = mfsRecordsSortedIdx.size() - 1; i >= 0; i--)
								{
									createChildNode((DefaultMutableTreeNode)un, (IFoundSet)mfsSorted.get(i), ((Integer)mfsRecordsSortedIdx.get(i)).intValue());
								}
							}
						}
					}
				}
			}
		}
		else if (obj instanceof RelationNode)
		{
			RelationNode un = (RelationNode)obj;
			if (!un.isInitialized())
			{
				un.flagInitialized();

				UserNode parentUserNode = (UserNode)un.getParent();
				IRecord parentRecord = parentUserNode.getRecord();

				IFoundSet nfs = parentRecord.getRelatedFoundSet(((RelationInfo)un.getUserObject()).getNRelationName());

				if (nfs != null && nfs.getSize() > 0)
				{
					for (int i = 0; i < nfs.getSize(); i++)
					{
						createChildNode(un, nfs, i);
						nfs.getRecord(i);
					}
				}
			}
		}
	}

	public class RelationNode extends DefaultMutableTreeNode
	{
		private final int id;
		private boolean didInit;

		public RelationNode(int id, RelationInfo relationInfo)
		{
			super(relationInfo);
			this.id = id;
		}

		public int getId()
		{
			return id;
		}

		@Override
		public int getChildCount()
		{
			if (!didInit)
			{
				lazyLoadChilderenIfNeeded(this);

			}
			return super.getChildCount();
		}

		private boolean isLeaf;

		@Override
		public boolean isLeaf()
		{
			if (!didInit)
			{
				isLeaf = !hasChild(this);
				if (isLeaf) setAllowsChildren(false);
			}

			return isLeaf;
		}


		public boolean isInitialized()
		{
			return didInit;
		}

		public void flagInitialized()
		{
			didInit = true;
		}

		@Override
		public String toString()
		{
			return ((RelationInfo)getUserObject()).getLabel();
		}
	}

	public class UserNode extends DefaultMutableTreeNode implements IModificationListener, Runnable
	{
		private static final long serialVersionUID = 1L;

		private boolean didInit;
		private final int recordIndex;
		private final IFoundSet foundSet;

		private Object exUserObject;

		private int nodeWidth;

		private boolean hasModificationListener;

		public UserNode(IFoundSet foundSet, int recordIndex)
		{
			this.foundSet = foundSet;
			this.recordIndex = recordIndex;
		}

		public void addModificationListener()
		{
			if (!hasModificationListener)
			{
				IRecord rec = getRecord();
				if (rec != null)
				{
					rec.addModificationListner(this);
					hasModificationListener = true;
				}
			}
		}

		public void removeModificationListener()
		{
			if (hasModificationListener)
			{
				IRecord rec = getRecord();
				if (rec != null)
				{
					rec.removeModificationListner(this);
					hasModificationListener = false;
				}
			}
		}

		public IFoundSet getFoundSet()
		{
			return foundSet;
		}

		public int getCurrentChildCount()
		{
			return super.getChildCount();
		}

		@Override
		public int getChildCount()
		{
			if (!didInit)
			{
				lazyLoadChilderenIfNeeded(this);
			}

			return super.getChildCount();
		}

		public void loadChildren()
		{
			synchronized (FoundSetTreeModel.this)
			{
				if (!didInit)
				{
					boolean isLeaf = !hasChild(this);
					if (isLeaf) setAllowsChildren(false);
					else lazyLoadChilderenIfNeeded(this);
				}
			}
		}

		private boolean isLeaf;

		@Override
		public boolean isLeaf()
		{
			if (!didInit)
			{
				isLeaf = !hasChild(this);
				if (isLeaf) setAllowsChildren(false);
				else application.getExecutor().execute(this);
			}

			return isLeaf;
		}

		public IRecord getRecord()
		{
			return foundSet.getRecord(recordIndex);
		}

		public int getRecordIndex()
		{
			return this.recordIndex;
		}

		public boolean isInitialized()
		{
			return didInit;
		}

		public void flagInitialized()
		{
			didInit = true;
		}

		public void setExUserObject(Object exUserObject)
		{
			this.exUserObject = exUserObject;
		}

		public Object getExUserObject()
		{
			return exUserObject;
		}

		public void setWidth(int width)
		{
			this.nodeWidth = width;
		}

		public int getWidth()
		{
			return this.nodeWidth;
		}

		@Override
		public boolean equals(Object unObj)
		{
			if (unObj != null && unObj instanceof UserNode)
			{
				UserNode un = (UserNode)unObj;

				return (un.getFoundSet() == this.foundSet) && (un.getRecordIndex() == this.recordIndex);
			}

			return false;
		}

		public boolean hasCheckBox()
		{
			return FoundSetTreeModel.this.bindingInfo.hasCheckBox(this);
		}

		public boolean isCheckBoxEnabled()
		{
			return FoundSetTreeModel.this.bindingInfo.isCheckBoxEnabled(this);
		}

		public boolean isCheckBoxChecked()
		{
			return FoundSetTreeModel.this.bindingInfo.isCheckBoxChecked(this);
		}

		public Icon getIcon()
		{
			return FoundSetTreeModel.this.bindingInfo.getIcon(this);
		}

		@Override
		public String toString()
		{
			String v = FoundSetTreeModel.this.bindingInfo.getText(this);
			return (v != null) ? v : "";
		}

		public void valueChanged(ModificationEvent e)
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					if (FoundSetTreeModel.this.tableModelListener != null)
						FoundSetTreeModel.this.tableModelListener.tableChanged(new TableModelEvent((ISwingFoundSet)foundSet, recordIndex));
				}
			});
		}

		public void run()
		{
			lazyLoadChilderenIfNeeded(this);
		}
	}

	private List getSortColumns(Table table, String sortColumnProvider)
	{
		List sortColumns = null;

		if (sortColumnProvider != null)
		{
			sortColumnProvider = sortColumnProvider.trim();

			StringTokenizer sortColumnProviderTokenizer = new StringTokenizer(sortColumnProvider, ",");

			String sortColumnToken;
			while (sortColumnProviderTokenizer.hasMoreTokens())
			{
				sortColumnToken = sortColumnProviderTokenizer.nextToken().trim();
				StringTokenizer sortColumnTokenizer = new StringTokenizer(sortColumnToken, " ");

				String sortColumnName = null;
				int sortOrder = SortColumn.ASCENDING;

				if (sortColumnTokenizer.hasMoreTokens()) sortColumnName = sortColumnTokenizer.nextToken();
				if (sortColumnTokenizer.hasMoreTokens())
				{
					String sOrder = sortColumnTokenizer.nextToken().toLowerCase();

					if (sOrder.equals("desc")) sortOrder = SortColumn.DESCENDING;
				}

				if (sortColumnName != null)
				{
					IColumn sortColumn = table.getColumn(sortColumnName);
					if (sortColumn != null)
					{
						SortColumn sCol = new SortColumn(sortColumn);
						sCol.setSortOrder(sortOrder);

						if (sortColumns == null) sortColumns = new ArrayList();

						sortColumns.add(sCol);
					}
				}
			}
		}

		return sortColumns;
	}

	public DefaultMutableTreeNode findNode(DefaultMutableTreeNode rootNode, IFoundSet foundSet, int recordIndex)
	{

		int childCount = 0;

		if (rootNode instanceof UserNode)
		{
			childCount = ((UserNode)rootNode).getCurrentChildCount();

			UserNode userNode = (UserNode)rootNode;

			if (userNode.getFoundSet().equals(foundSet) && userNode.getRecordIndex() == recordIndex)
			{
				return userNode;
			}
		}
		else
		{
			childCount = rootNode.getChildCount();
		}

		if (childCount > 0)
		{
			DefaultMutableTreeNode childNode, foundNode;

			for (int i = 0; i < childCount; i++)
			{
				childNode = (DefaultMutableTreeNode)rootNode.getChildAt(i);
				foundNode = findNode(childNode, foundSet, recordIndex);
				if (foundNode != null)
				{
					return foundNode;
				}
			}
		}

		return null;

	}
}
