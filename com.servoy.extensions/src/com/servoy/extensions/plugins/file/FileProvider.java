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
package com.servoy.extensions.plugins.file;

import java.awt.Desktop;
import java.awt.Window;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;

import com.servoy.base.scripting.annotations.ServoyClientSupport;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.Messages;
import com.servoy.j2db.documentation.ServoyDocumented;
import com.servoy.j2db.plugins.IClientPluginAccess;
import com.servoy.j2db.plugins.IMediaUploadCallback;
import com.servoy.j2db.plugins.IRuntimeWindow;
import com.servoy.j2db.plugins.ISmartRuntimeWindow;
import com.servoy.j2db.plugins.IUploadData;
import com.servoy.j2db.scripting.FunctionDefinition;
import com.servoy.j2db.scripting.IReturnedTypesProvider;
import com.servoy.j2db.scripting.IScriptable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.FileChooserUtils;

/**
 * @author jcompagner
 * @author Servoy Stuff
 */
@ServoyDocumented(publicName = FilePlugin.PLUGIN_NAME, scriptingName = "plugins." + FilePlugin.PLUGIN_NAME)
public class FileProvider implements IReturnedTypesProvider, IScriptable
{

	protected final FilePlugin plugin;
	private final Map<String, File> tempFiles = new HashMap<String, File>(); //check this map when saving (txt/binary) files
	private static final JSFile[] EMPTY = new JSFile[0];
	private final Timer timer;
	private final List<JSFile> trackedFiles = new ArrayList<JSFile>();

	/**
	 * Line Separator constant, used to append to Text file
	 * @since Servoy 5.2
	 */
	private static final String LF = System.getProperty("line.separator"); //$NON-NLS-1$

	/**
	 * Size of the buffer used to stream files to the server
	 * @since Servoy 5.2
	 */
	static final int CHUNK_BUFFER_SIZE = 64 * 1024;

	public FileProvider(FilePlugin plugin)
	{
		this.plugin = plugin;
		this.timer = new Timer();
	}

	// default constructor, used for documentation generation
	public FileProvider()
	{
		this.plugin = null;
		this.timer = null; // do not create a Timer here, it will not be cleaned up
	}


	/**
	 * Returns a JSFile instance that corresponds to the Desktop folder of the currently logged in user.
	 *
	 * @sample
	 * var d = plugins.file.getDesktopFolder();
	 * application.output('desktop folder is: ' + d.getAbsolutePath());
	 */
	public JSFile js_getDesktopFolder()
	{
		FileSystemView fsf = FileSystemView.getFileSystemView();
		// in Unix-based systems, the root directory is "/", not ~<user>/Desktop => old implementation not correct
		// in Windows the root directory is indeed the desktop directory, but it's also <user.home>\Desktop
		File homeDir = fsf.getHomeDirectory();
		File desktopDir = new File(homeDir.getAbsolutePath() + File.separator + "Desktop"); //$NON-NLS-1$
		if (desktopDir != null && desktopDir.isDirectory())
		{
			return new JSFile(desktopDir);
		}
		else
		{
			//the old implementation - shouldn't normally reach this piece of code
			File[] roots = fsf.getRoots();
			for (File element : roots)
			{
				if (fsf.isRoot(element))
				{
					return new JSFile(element);
				}
			}
			return null;
		}
	}

	/**
	 * Returns a JSFile instance corresponding to the home folder of the logged in used.
	 *
	 * @sample
	 * var d = plugins.file.getHomeFolder();
	 * application.output('home folder: ' + d.getAbsolutePath());
	 */
	public JSFile js_getHomeFolder()
	{
		return new JSFile(new File(System.getProperty("user.home"))); //$NON-NLS-1$
	}

	/**
	 * @deprecated Replaced by {@link #getHomeFolder()}.
	 */
	@Deprecated
	public JSFile js_getHomeDirectory()
	{
		return new JSFile(new File(System.getProperty("user.home"))); //$NON-NLS-1$
	}

	protected File getFileFromArg(Object f, boolean createFileInstance)
	{
		File file = null;
		if (f instanceof String)
		{
			file = tempFiles.get(f);
			if (file == null && createFileInstance) file = new File((String)f);
		}
		else if (f instanceof JSFile)
		{
			file = ((JSFile)f).getFile();
		}
		else if (f instanceof File)
		{
			file = (File)f;
		}
		return file;
	}

	/**
	 * Shows a file open dialog. Filters can be applied on what type of files can be selected. (Web Enabled, you must set the callback method for this to work)
	 *
	 * @sample
	 * // This selects only files ('1'), previous dir must be used ('null'), no multiselect ('false') and
	 * // the filter "JPG and GIF" should be used: ('new Array("JPG and GIF","jpg","gif")').
	 * /** @type {JSFile} *&#47;
	 * var f = plugins.file.showFileOpenDialog(1, null, false, new Array("JPG and GIF", "jpg", "gif"));
	 * application.output('File: ' + f.getName());
	 * application.output('is dir: ' + f.isDirectory());
	 * application.output('is file: ' + f.isFile());
	 * application.output('path: ' + f.getAbsolutePath());
	 *
	 * // This allows mutliple selection of files, using previous dir and the same filter as above. This also casts the result to the JSFile type using JSDoc.
	 * // if filters are specified, "all file" filter will not show up unless "*" filter is present
	 * /** @type {JSFile[]} *&#47;
	 * var files = plugins.file.showFileOpenDialog(1, null, true, new Array("JPG and GIF", "jpg", "gif", "*"));
	 * for (var i = 0; i < files.length; i++)
	 * {
	 * 	 application.output('File: ' + files[i].getName());
	 * 	 application.output('content type: ' + files[i].getContentType());
	 * 	 application.output('last modified: ' + files[i].lastModified());
	 * 	 application.output('size: ' + files[i].size());
	 * }
	 * //for the web and NG you have to give a callback function that has a JSFile array as its first argument (also works in smart), only multi select and the title are used in the webclient, others are ignored
	 * plugins.file.showFileOpenDialog(null,null,false,new Array("JPG and GIF", "jpg", "gif"),mycallbackfunction,'Select some nice files')
	 *
	 * When handling big files please look at the admin page properties: "servoy.ng_web_client.tempfile.threshold" and "servoy.ng_web_client.temp.uploadir", so that big files are mapped to temp files and saved to a good temp dir
	 * so that in the callback method you can try to rename the temp generated file to something on the filesystem with a specific name. This way there is no need to stream anything again on the server side
	 * (or access the bytes which will load the big file completely in memory)
	 *
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog()
	{
		return js_showFileOpenDialog(Integer.valueOf(1), (String)null, Boolean.FALSE, null, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Function callbackfunction)
	{
		return js_showFileOpenDialog(Integer.valueOf(1), (String)null, Boolean.FALSE, null, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode)
	{
		return js_showFileOpenDialog(selectionMode, (String)null, Boolean.FALSE, null, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, (String)null, Boolean.FALSE, null, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder; null=default/previous
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, Boolean.FALSE, null, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder; null=default/previous
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, Boolean.FALSE, null, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder,null=default/previous
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, Boolean.FALSE, null, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder,null=default/previous
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, Boolean.FALSE, null, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder, null=default/previous
	 * @param multiselect true/false
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory, Boolean multiselect)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, null, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder, null=default/previous
	 * @param multiselect true/false
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory, Boolean multiselect)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, null, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder,null=default/previous
	 * @param multiselect true/false
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory, Boolean multiselect, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, null, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder,null=default/previous
	 * @param multiselect true/false
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory, Boolean multiselect, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, null, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder,null=default/previous
	 * @param multiselect true/false
	 * @param filter A filter or array of filters on the folder files.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory, Boolean multiselect, Object filter)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, filter, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder,null=default/previous
	 * @param multiselect true/false
	 * @param filter A filter or array of filters on the folder files.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory, Boolean multiselect, Object filter)
	{

		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, filter, null, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder,null=default/previous
	 * @param multiselect true/false
	 * @param filter A filter or array of filters on the folder files.
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory, Boolean multiselect, Object filter, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, filter, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder,null=default/previous
	 * @param multiselect true/false
	 * @param filter A filter or array of filters on the folder files.
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory, Boolean multiselect, Object filter, Function callbackfunction)
	{
		return js_showFileOpenDialog(selectionMode, startDirectory, multiselect, filter, callbackfunction, null);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory JSFile instance of default folder, null=default/previous
	 * @param multiselect true/false
	 * @param filter A filter or array of filters on the folder files.
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 * @param title The tile of the dialog
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, JSFile startDirectory, Boolean multiselect, Object filter, Function callbackfunction,
		String title)
	{
		return showFileOpenDialog(selectionMode, startDirectory, multiselect, filter, callbackfunction, title);
	}

	/**
	 * @clonedesc js_showFileOpenDialog()
	 * @sampleas js_showFileOpenDialog()
	 *
	 * @param selectionMode 0=both,1=Files,2=Dirs
	 * @param startDirectory Path to default folder, null=default/previous
	 * @param multiselect true/false
	 * @param filter A filter or array of filters on the folder files.
	 * @param callbackfunction A function that takes the (JSFile) array of the selected files as first argument
	 * @param title The tile of the dialog
	 */
	@ServoyClientSupport(ng = true, wc = true, sc = true)
	public Object js_showFileOpenDialog(Number selectionMode, String startDirectory, Boolean multiselect, Object filter, Function callbackfunction,
		String title)
	{
		return showFileOpenDialog(selectionMode, startDirectory, multiselect, filter, callbackfunction, title);
	}


	private Object showFileOpenDialog(Number selectionMode, Object startDirectory, Boolean multiselect, Object filter, Function callbackfunction, String title)
	{
		int _selectionMode = getNumberAsInt(selectionMode, 1);
		boolean _multiselect = getBooleanAsbool(multiselect, false);

		int selection = 1;
		switch (_selectionMode)
		{
			case 0 :
				selection = JFileChooser.FILES_AND_DIRECTORIES;
				break;
			case 2 :
				selection = JFileChooser.DIRECTORIES_ONLY;
				break;
			default :
				selection = JFileChooser.FILES_ONLY;
		}

		File file = startDirectory != null ? getFileFromArg(startDirectory, true) : null;
		FunctionDefinition fd = callbackfunction != null ? new FunctionDefinition(callbackfunction) : null;
		String[] filterA = null;

		if (filter instanceof String)
		{
			filterA = new String[] { (String)filter };
		}
		else if (filter instanceof Object[])
		{
			Object[] array = (Object[])filter;
			filterA = new String[array.length];
			for (int i = 0; i < array.length; i++)
			{
				filterA[i] = array[i].toString();
			}
		}

		IClientPluginAccess access = plugin.getClientPluginAccess();
		if (fd != null)
		{
			final FunctionDefinition functionDef = fd;
			final List<JSFile> returnList = new ArrayList<JSFile>();
			IMediaUploadCallback callback = new IMediaUploadCallback()
			{
				public void uploadComplete(IUploadData[] fu)
				{
					if (fu.length > 0)
					{
						JSFile[] files = new JSFile[fu.length];
						for (int i = 0; i < fu.length; i++)
						{
							files[i] = new JSFile(fu[i]);
							returnList.add(files[i]);
						}
						functionDef.executeSync(plugin.getClientPluginAccess(), new Object[] { files });
					}
				}

				public void onSubmit()
				{
					// submit without uploaded files
				}
			};
			access.showFileOpenDialog(callback, file != null ? file.getAbsolutePath() : null, _multiselect, filterA, selection, title);
			if (returnList.size() > 0) return returnList.toArray(new JSFile[returnList.size()]);
		}
		else
		{
			if (access.getApplicationType() == IClientPluginAccess.WEB_CLIENT || access.getApplicationType() == IClientPluginAccess.NG_CLIENT)
			{
				throw new RuntimeException("Function callback not set for webclient/ngclient"); //$NON-NLS-1$
			}

			IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
			Window currentWindow = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();

			if (_multiselect)
			{
				File[] files = FileChooserUtils.getFiles(currentWindow, file, selection, filterA, title);
				JSFile[] convertedFiles = convertToJSFiles(files);
				return convertedFiles;
			}
			else
			{
				File f = FileChooserUtils.getAReadFile(currentWindow, file, selection, filterA, title);
				if (f != null)
				{
					return new JSFile(f);
				}
			}
		}
		return null;
	}

	/**
	 * Returns an array of JSFile instances corresponding to content of the specified folder. The content can be filtered by optional name filter(s), by type, by visibility and by lock status.
	 *
	 * @sample
	 * var files = plugins.file.getFolderContents('stories', '.txt');
	 * for (var i=0; i<files.length; i++)
	 * 	application.output(files[i].getAbsolutePath());
	 *
	 * @param targetFolder JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 * @param lockedOption 1=locked, 2=nonlocked
	 */
	public JSFile[] js_getFolderContents(JSFile targetFolder, Object fileFilter, final Number fileOption, final Number visibleOption, final Number lockedOption)
	{
		return getFolderContents(targetFolder, fileFilter, fileOption, visibleOption, lockedOption);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder File path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 * @param lockedOption 1=locked, 2=nonlocked
	 */
	public JSFile[] js_getFolderContents(String targetFolder, Object fileFilter, final Number fileOption, final Number visibleOption, final Number lockedOption)
	{
		return getFolderContents(targetFolder, fileFilter, fileOption, visibleOption, lockedOption);
	}

	private JSFile[] getFolderContents(Object targetFolder, Object fileFilter, final Number fileOption, final Number visibleOption, final Number lockedOption)
	{
		final int _fileOption = getNumberAsInt(fileOption, AbstractFile.ALL);
		final int _visibleOption = getNumberAsInt(visibleOption, AbstractFile.ALL);
		final int _lockedOption = getNumberAsInt(lockedOption, AbstractFile.ALL);

		if (targetFolder == null) return EMPTY;

		final String[] fileFilterOptions;

		if (fileFilter != null)
		{
			if (fileFilter.getClass().isArray())
			{
				Object[] tmp = (Object[])fileFilter;
				fileFilterOptions = new String[tmp.length];
				for (int i = 0; i < tmp.length; i++)
				{
					fileFilterOptions[i] = ((String)tmp[i]).toLowerCase();
				}
			}
			else
			{
				fileFilterOptions = new String[] { ((String)fileFilter).toLowerCase() };
			}
		}
		else
		{
			fileFilterOptions = null;
		}

		File file = convertToFile(targetFolder);

		FileFilter ff = new FileFilter()
		{
			public boolean accept(File pathname)
			{
				boolean retVal = true;
				if (fileFilterOptions != null)
				{
					String name = pathname.getName().toLowerCase();
					for (String element : fileFilterOptions)
					{
						retVal = name.endsWith(element);
						if (retVal) break;
					}
				}
				if (!retVal) return retVal;

				// file or folder
				if (_fileOption == AbstractFile.FILES)
				{
					retVal = pathname.isFile();
				}
				else if (_fileOption == AbstractFile.FOLDERS)
				{
					retVal = pathname.isDirectory();
				}
				if (!retVal) return false;

				boolean hidden = pathname.isHidden();
				if (_visibleOption == AbstractFile.VISIBLE) retVal = !hidden;
				else if (_visibleOption == AbstractFile.NON_VISIBLE) retVal = hidden;
				if (!retVal) return false;

				boolean canWrite = pathname.canWrite();
				if (_lockedOption == AbstractFile.LOCKED) retVal = !canWrite;
				else if (_lockedOption == AbstractFile.NON_LOCKED) retVal = canWrite;
				return retVal;
			}
		};
		return convertToJSFiles(file.listFiles(ff));
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 */
	public JSFile[] js_getFolderContents(JSFile targetFolder, Object fileFilter, final Number fileOption, final Number visibleOption)
	{
		return js_getFolderContents(targetFolder, fileFilter, fileOption, visibleOption, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder File path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 */
	public JSFile[] js_getFolderContents(String targetFolder, Object fileFilter, final Number fileOption, final Number visibleOption)
	{
		return js_getFolderContents(targetFolder, fileFilter, fileOption, visibleOption, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 */
	public JSFile[] js_getFolderContents(JSFile targetFolder, Object fileFilter, final Number fileOption)
	{
		return js_getFolderContents(targetFolder, fileFilter, fileOption, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder File path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 */
	public JSFile[] js_getFolderContents(String targetFolder, Object fileFilter, final Number fileOption)
	{
		return js_getFolderContents(targetFolder, fileFilter, fileOption, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 */
	public JSFile[] js_getFolderContents(JSFile targetFolder, Object fileFilter)
	{
		return js_getFolderContents(targetFolder, fileFilter, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder File path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 */
	public JSFile[] js_getFolderContents(String targetFolder, Object fileFilter)
	{
		return js_getFolderContents(targetFolder, fileFilter, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder JSFile object.
	 */
	public JSFile[] js_getFolderContents(JSFile targetFolder)
	{
		return js_getFolderContents(targetFolder, null, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getFolderContents(JSFile,Object,Number,Number,Number)
	 * @sampleas js_getFolderContents(JSFile,Object,Number,Number,Number)
	 *
	 * @param targetFolder File path.
	 */
	public JSFile[] js_getFolderContents(String targetFolder)
	{
		return js_getFolderContents(targetFolder, null, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @param files
	 * @return
	 */
	private JSFile[] convertToJSFiles(File[] files)
	{
		if (files == null) return EMPTY;
		JSFile[] jsFiles = new JSFile[files.length];
		for (int i = 0; i < files.length; i++)
		{
			jsFiles[i] = new JSFile(files[i]);
		}
		return jsFiles;
	}

	/**
	 * Moves the file from the source to the destination place. Returns true on success, false otherwise.
	 *
	 * @sample
	 * // Move file based on names.
	 * if (!plugins.file.moveFile('story.txt','story.txt.new'))
	 * 	application.output('File move failed.');
	 * // Move file based on JSFile instances.
	 * var f = plugins.file.convertToJSFile('story.txt.new');
	 * var fmoved = plugins.file.convertToJSFile('story.txt');
	 * if (!plugins.file.moveFile(f, fmoved))
	 * 	application.output('File move back failed.');
	 *
	 * @param source
	 * @param destination
	 */
	public boolean js_moveFile(Object source, Object destination)
	{
		File sourceFile = convertToFile(source);
		File destFile = convertToFile(destination);
		if (sourceFile == null || destFile == null) return false;

		if (destFile.exists())
		{
			destFile.delete();
		}
		else
		{
			destFile.getAbsoluteFile().getParentFile().mkdirs();
		}

		if (sourceFile.equals(destFile)) return false;

		if (!sourceFile.renameTo(destFile))
		{
			// rename wouldn't work copy it
			if (!js_copyFile(sourceFile, destFile))
			{
				return false;
			}
			sourceFile.delete();
		}
		return true;
	}

	/**
	 * returns a JSFile for the given string
	 *
	 * @deprecated Replaced by {@link #convertToJSFile(Object)}.
	 *
	 * @param fileName
	 */
	@Deprecated
	public JSFile js_convertStringToJSFile(String fileName)
	{
		return new JSFile(new File(fileName));
	}

	/**
	 * Returns a JSFile instance corresponding to an alternative representation of a file (for example a string).
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile("story.txt");
	 * if (f.canRead())
	 * 	application.output("File can be read.");
	 *
	 * @param file
	 *
	 * @return JSFile
	 */
	public JSFile js_convertToJSFile(Object file)
	{
		if (file instanceof JSFile) return (JSFile)file;
		if (file instanceof File) return new JSFile((File)file);
		if (file instanceof IUploadData) return new JSFile((IUploadData)file);
		if (file != null) return new JSFile(new File(file.toString()));
		return null;
	}

	private File convertToFile(Object source)
	{
		if (source instanceof JSFile) return ((JSFile)source).getFile();
		else if (source instanceof File) return (File)source;
		else if (source != null) return new File(source.toString());
		return null;
	}

	/**
	 * Copies the sourcefolder to the destination folder, recursively. Returns true if the copy succeeds, false if any error occurs.
	 *
	 * @sample
	 * // Copy folder based on names.
	 * if (!plugins.file.copyFolder("stories", "stories_copy"))
	 * 	application.output("Folder copy failed.");
	 * // Copy folder based on JSFile instances.
	 * var d = plugins.file.createFile("stories");
	 * var dcopy = plugins.file.createFile("stories_copy_2");
	 * if (!plugins.file.copyFolder(d, dcopy))
	 * 	application.output("Folder copy failed.");
	 *
	 * @param source
	 * @param destination
	 *
	 * @return success boolean
	 */
	public boolean js_copyFolder(Object source, Object destination)
	{
		File sourceDir = convertToFile(source);
		File destDir = convertToFile(destination);
		if (sourceDir == null || destDir == null) return false;

		if (sourceDir.equals(destDir)) return false;

		if (!sourceDir.exists()) return false;
		if (!sourceDir.isDirectory())
		{
			return js_copyFile(sourceDir, destDir);
		}

		if (destDir.exists())
		{
			if (!destDir.isDirectory())
			{
				return false;
			}
		}
		else if (!destDir.mkdirs())
		{
			return false;
		}

		boolean succes = true;
		File[] files = sourceDir.listFiles();
		if (files != null && files.length > 0)
		{
			for (File file : files)
			{
				File dest = new File(destDir, file.getName());
				if (file.isDirectory())
				{
					if (!file.equals(destDir))
					{
						succes = (js_copyFolder(file, dest) && succes);
						if (!succes) return false;
					}
				}
				else
				{
					succes = (js_copyFile(file, dest) && succes);
					if (!succes) return false;
				}
			}
		}
		return succes;
	}

	/**
	 * Copies the source file to the destination file. Returns true if the copy succeeds, false if any error occurs.
	 *
	 * @sample
	 * // Copy based on file names.
	 * if (!plugins.file.copyFile("story.txt", "story.txt.copy"))
	 * 	application.output("Copy failed.");
	 * // Copy based on JSFile instances.
	 * var f = plugins.file.createFile("story.txt");
	 * var fcopy = plugins.file.createFile("story.txt.copy2");
	 * if (!plugins.file.copyFile(f, fcopy))
	 * 	application.output("Copy failed.");
	 *
	 * @param source
	 * @param destination
	 */
	public boolean js_copyFile(Object source, Object destination)
	{
		File sourceFile = convertToFile(source);
		File destFile = convertToFile(destination);
		if (sourceFile == null || destFile == null) return false;

		if (sourceFile.equals(destFile)) return false;

		try
		{
			if (destFile.exists())
			{
				destFile.delete();
			}
			else
			{
				destFile.getAbsoluteFile().getParentFile().mkdirs();
			}
			if (destFile.createNewFile())
			{
				FileInputStream fis = null;
				FileOutputStream fos = null;
				FileChannel sourceChannel = null;
				FileChannel destinationChannel = null;


				try
				{
					fis = new FileInputStream(sourceFile);
					fos = new FileOutputStream(destFile);
					sourceChannel = fis.getChannel();
					destinationChannel = fos.getChannel();
					// Copy source file to destination file
					destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
				}
				catch (Exception e1)
				{
					throw e1;
				}
				finally
				{
					try
					{
						if (sourceChannel != null) sourceChannel.close();
					}
					catch (Exception e)
					{
					}
					try
					{
						if (destinationChannel != null) destinationChannel.close();
					}
					catch (Exception e)
					{
					}
					try
					{
						if (fis != null) fis.close();
					}
					catch (Exception e)
					{
					}
					try
					{
						if (fos != null) fos.close();
					}
					catch (Exception e)
					{
					}
				}
				return true;
			}
		}
		catch (Exception e)
		{
			// handle any IOException
			Debug.error(e);
		}
		return false;

	}

	/**
	 * Creates the folder by the given pathname, including anynecessary but nonexistent parent folders.
	 * Note that if this operation fails it may have succeeded in creating some of the necessary parent folders.
	 * Will return true if it could make this folder or if the folder did already exist.
	 *
	 * @sample
	 * var d = plugins.file.convertToJSFile("newfolder");
	 * if (!plugins.file.createFolder(d))
	 * 	application.output("Folder could not be created.");
	 *
	 * @param destination
	 */
	public boolean js_createFolder(Object destination)
	{
		File destFile = convertToFile(destination);
		if (destFile == null) return false;

		boolean b = (destFile.exists() && destFile.isDirectory());
		if (!b)
		{
			b = destFile.mkdirs();
		}
		return b;
	}

	/**
	 * Removes a file from disk. Returns true on success, false otherwise.
	 *
	 * @sample
	 * if (plugins.file.deleteFile('story.txt'))
	 * 	application.output('File deleted.');
	 *
	 * //In case the file to delete is a remote file:
	 * var file = plugins.file.convertToRemoteJSFile('/story.txt');
	 * plugins.file.deleteFile(file);
	 *
	 * @param destination
	 */
	public boolean js_deleteFile(Object destination)
	{
		if (destination instanceof JSFile && ((JSFile)destination).getAbstractFile() instanceof RemoteFile)
		{
			return ((JSFile)destination).js_deleteFile();
		}
		return js_deleteFolder(destination, true);
	}

	/**
	 * Deletes a folder from disk recursively. Returns true on success, false otherwise. If the second parameter is set to true, then a warning will be issued to the user before actually removing the folder.
	 *
	 * @sample
	 * if (plugins.file.deleteFolder('stories', true))
	 * 	application.output('Folder deleted.');
	 *
	 * //In case the file to delete is a remote folder:
	 * plugins.file.deleteFolder(plugins.file.convertToRemoteJSFile('/stories'), true);
	 *
	 * @param destination
	 * @param showWarning
	 */
	public boolean js_deleteFolder(Object destination, boolean showWarning)
	{
		File destFile = convertToFile(destination);
		if (destFile == null) return false;

		if (destFile.isDirectory())
		{
			if (showWarning && plugin.getClientPluginAccess().getApplicationType() == IApplication.CLIENT)
			{
				IClientPluginAccess access = plugin.getClientPluginAccess();
				IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
				Window currentWindow = null;
				if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
				int option = JOptionPane.showConfirmDialog(currentWindow,
					Messages.getString("servoy.plugin.file.folderDelete.warning") + destFile.getAbsolutePath(), //$NON-NLS-1$
					Messages.getString("servoy.plugin.file.folderDelete.title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$
				if (option != JOptionPane.YES_OPTION) return false;
			}
			File[] files = destFile.listFiles();
			for (File element : files)
			{
				js_deleteFolder(element, false);
			}
		}
		return destFile.delete();
	}

	/**
	 * Returns the size of the specified file.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * application.output('file size: ' + plugins.file.getFileSize(f));
	 *
	 * //In case the file is remote, located on the server side inside the default upload folder:
	 * var f = plugins.file.convertToRemoteJSFile('/story.txt');
	 * application.output('file size: ' + plugins.file.getFileSize(f));
	 *
	 * @param fileOrPath can be a (remote) JSFile or a local file path
	 */
	public long js_getFileSize(Object fileOrPath)
	{
		if (fileOrPath instanceof JSFile && ((JSFile)fileOrPath).getAbstractFile() instanceof RemoteFile)
		{
			return ((JSFile)fileOrPath).js_size();
		}
		File file = convertToFile(fileOrPath);
		if (file == null) return -1;
		return file.length();
	}

	/**
	 * Returns the modification date of a file.
	 *
	 * @sample
	 * var f = plugins.file.convertToJSFile('story.txt');
	 * application.output('last changed: ' + plugins.file.getModificationDate(f));
	 *
	 * //In case the file is remote, located on the server side inside the default upload folder:
	 * var f = plugins.file.convertToRemoteJSFile('/story.txt');
	 * application.output('file size: ' + plugins.file.getModificationDate(f));
	 *
	 * @param fileOrPath can be a (remote) JSFile or a local file path
	 */
	public Date js_getModificationDate(Object fileOrPath)
	{
		if (fileOrPath instanceof JSFile && ((JSFile)fileOrPath).getAbstractFile() instanceof RemoteFile)
		{
			return ((JSFile)fileOrPath).js_lastModified();
		}
		File file = convertToFile(fileOrPath);
		if (file == null) return null;
		return new Date(file.lastModified());
	}

	/**
	 * Returns an Array of JSFile instances correponding to the file system root folders.
	 *
	 * @sample
	 * var roots = plugins.file.getDiskList();
	 * for (var i = 0; i < roots.length; i++)
	 * 	application.output(roots[i].getAbsolutePath());
	 */
	public JSFile[] js_getDiskList()
	{
		File[] roots = File.listRoots();
		JSFile[] jsRoots = new JSFile[roots.length];
		for (int i = 0; i < roots.length; i++)
		{
			jsRoots[i] = new JSFile(roots[i]);
		}
		return jsRoots;
	}

	/**
	 * Creates a temporary file on disk. A prefix and an extension are specified and they will be part of the file name.
	 *
	 * @sample
	 * var tempFile = plugins.file.createTempFile('myfile','.txt');
	 * application.output('Temporary file created as: ' + tempFile.getAbsolutePath());
	 * plugins.file.writeTXTFile(tempFile, 'abcdefg');
	 *
	 * @param prefix
	 * @param suffix
	 */
	@SuppressWarnings("nls")
	public JSFile js_createTempFile(String prefix, String suffix)
	{
		try
		{
			// If shorter than three, then pad with something, so that we don't get exception.
			File f = File.createTempFile((prefix.length() < 3) ? (prefix + "svy") : prefix, suffix);
			f.deleteOnExit();
			String name = f.getAbsolutePath();
			tempFiles.put(name, f);
			return new JSFile(f);
		}
		catch (Exception e)
		{
			Debug.error(e);
			return null;
		}
	}

	/**
	 * Creates a JSFile instance. Does not create the file on disk.
	 *
	 * @sample
	 * // Create the JSFile instance based on the file name.
	 * var f = plugins.file.createFile("newfile.txt");
	 * // Create the file on disk.
	 * if (!f.createNewFile())
	 * 	application.output("The file could not be created.");
	 *
	 * @param targetFile
	 */
	public JSFile js_createFile(Object targetFile)
	{
		return js_convertToJSFile(targetFile);
	}

	/**
	 * Writes data into a text file. (Web Enabled: file parameter can be a string 'mytextfile.txt' to hint the browser what it is, if it is a JSFile instance it will be saved on the server)
	 *
	 * @sample
	 * var fileNameSuggestion = 'myspecialexport.tab'
	 * var textData = 'load of data...'
	 * var success = plugins.file.writeTXTFile(fileNameSuggestion, textData);
	 * if (!success) application.output('Could not write file.');
	 * // For file-encoding parameter options (default OS encoding is used), http://download.oracle.com/javase/1.4.2/docs/guide/intl/encoding.doc.html
	 * // mimeType variable can be left null, and is used for webclient only. Specify one of any valid mime types as referenced here: http://www.w3schools.com/media/media_mimeref.asp'
	 *
	 * @param file JSFile
	 * @param text_data Text to be written.
	 *
	 * @return Success boolean.
	 */
	public boolean js_writeTXTFile(JSFile file, String text_data)
	{
		return js_writeTXTFile(file, text_data, null, null);
	}

	/**
	 * @clonedesc js_writeTXTFile(JSFile, String)
	 * @sampleas js_writeTXTFile(JSFile, String)
	 *
	 * @param file The file path.
	 * @param text_data Text to be written.
	 */
	public boolean js_writeTXTFile(String file, String text_data)
	{
		return js_writeTXTFile(file, text_data, null, null);
	}

	/**
	 * @clonedesc js_writeTXTFile(JSFile, String)
	 * @sampleas js_writeTXTFile(JSFile, String)
	 *
	 * @param file JSFile
	 * @param text_data Text to be written.
	 * @param charsetname Charset name.
	 *
	 * @return Success boolean.
	 */
	public boolean js_writeTXTFile(JSFile file, String text_data, String charsetname)
	{
		return js_writeTXTFile(file, text_data, charsetname, null);
	}

	/**
	 * @clonedesc js_writeTXTFile(JSFile, String)
	 * @sampleas js_writeTXTFile(JSFile, String)
	 *
	 * @param file The file path.
	 * @param text_data Text to be written.
	 * @param charsetname Charset name.
	 */
	public boolean js_writeTXTFile(String file, String text_data, String charsetname)
	{
		return js_writeTXTFile(file, text_data, charsetname, null);
	}

	/**
	 * @clonedesc js_writeTXTFile(JSFile, String)
	 * @sampleas js_writeTXTFile(JSFile, String)
	 *
	 * @param file JSFile
	 * @param text_data Text to be written.
	 * @param charsetname Charset name.
	 * @param mimeType Content type (used only on web).
	 *
	 * @return Success boolean.
	 */
	public boolean js_writeTXTFile(JSFile file, String text_data, String charsetname, String mimeType)
	{
		return writeTXTFile(file, text_data, charsetname, mimeType);
	}

	/**
	 * @clonedesc js_writeTXTFile(JSFile, String)
	 * @sampleas js_writeTXTFile(JSFile, String)
	 *
	 * @param file The file path.
	 * @param text_data Text to be written.
	 * @param charsetname Charset name.
	 * @param mimeType Content type (used only on web).
	 */
	public boolean js_writeTXTFile(String file, String text_data, String charsetname, String mimeType)
	{
		return writeTXTFile(file, text_data, charsetname, mimeType);
	}

	@SuppressWarnings("nls")
	private boolean writeTXTFile(Object file, String text_data, String charsetname, String mimeType)
	{
		if (file == null) return false;
		return writeTXT(file, text_data == null ? "" : text_data, charsetname, mimeType == null ? "text/plain" : mimeType);
	}


	/**
	 * @param f
	 * @param data
	 * @param encoding
	 * @return
	 */
	protected boolean writeTXT(Object f, String data, String encoding, @SuppressWarnings("unused") String contentType)
	{
		try
		{
			IClientPluginAccess access = plugin.getClientPluginAccess();
			File file = getFileFromArg(f, true);
			if (file == null)
			{
				IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
				Window currentWindow = null;
				if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
				file = FileChooserUtils.getAWriteFile(currentWindow, file, false);
			}

			if (file != null)
			{
				FileOutputStream fos = new FileOutputStream(file);
				try
				{
					return writeToOutputStream(fos, data, encoding);
				}
				finally
				{
					fos.close();
				}
			}
			return false;
		}
		catch (Exception e)
		{
			Debug.error(e);
			return false;
		}
	}

	/**
	 * @param data
	 * @param encoding
	 * @param file
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected boolean writeToOutputStream(OutputStream os, String data, String encoding) throws FileNotFoundException, IOException
	{
		Charset cs = null;
		if (encoding != null)
		{
			if (Charset.isSupported(encoding))
			{
				cs = Charset.forName(encoding);
			}
			else
			{
				return false;//unknown encoding
			}
		}

		OutputStreamWriter writer = null;
		if (cs != null)
		{
			writer = new OutputStreamWriter(os, cs);
		}
		else
		{
			writer = new OutputStreamWriter(os);//default char encoding
		}
		BufferedWriter bw = new BufferedWriter(writer);
		bw.write(data);
		bw.close();
		return true;
	}

	/**
	 * @clonedesc js_writeXMLFile(JSFile, String)
	 * @sampleas js_writeXMLFile(JSFile, String)
	 *
	 * @param file a local JSFile
	 * @param xml_data the xml data to write
	 * @param encoding the specified encoding
	 */
	public boolean js_writeXMLFile(JSFile file, String xml_data, String encoding)
	{
		return writeXMLFile(file, xml_data, encoding);
	}

	/**
	 * @clonedesc js_writeXMLFile(JSFile, String)
	 * @sampleas js_writeXMLFile(JSFile, String)
	 *
	 * @param file the file path as a String
	 * @param xml_data the xml data to write
	 * @param encoding the specified encoding
	 */
	public boolean js_writeXMLFile(String file, String xml_data, String encoding)
	{
		return writeXMLFile(file, xml_data, encoding);
	}

	@SuppressWarnings("nls")
	private boolean writeXMLFile(Object file, String xml_data, String encoding)
	{
		if (xml_data == null) return false;
		return writeTXT(file == null ? "file.xml" : file, xml_data, encoding, "text/xml");
	}

	/**
	 * Writes data into an XML file. The file is saved with the encoding specified by the XML itself. (Web Enabled: file parameter can be a string 'myxmlfile.xml' to hint the browser what it is, if it is a JSFile instance it will be saved on the server)
	 *
	 * @sample
	 * var fileName = 'form.xml'
	 * var xml = controller.printXML()
	 * var success = plugins.file.writeXMLFile(fileName, xml);
	 * if (!success) application.output('Could not write file.');
	 *
	 * @param file a local JSFile
	 * @param xml_data the xml data to write
	 */
	public boolean js_writeXMLFile(JSFile file, String xml_data)
	{
		return writeXMLFile(file, xml_data);
	}

	/**
	 * @clonedesc js_writeXMLFile(JSFile,String)
	 * @sampleas js_writeXMLFile(JSFile,String)
	 *
	 * @param file the file path as a String
	 * @param xml_data the xml data to write
	 */
	public boolean js_writeXMLFile(String file, String xml_data)
	{
		return writeXMLFile(file, xml_data);
	}

	@SuppressWarnings("nls")
	private boolean writeXMLFile(Object file, String xml_data)
	{
		if (xml_data == null) return false;

		String encoding = "UTF-8";
		int idx1 = xml_data.indexOf("encoding=");
		if (idx1 != -1 && xml_data.length() > idx1 + 10)
		{
			int idx2 = xml_data.indexOf('"', idx1 + 10);
			int idx3 = xml_data.indexOf('\'', idx1 + 10);
			int idx4 = Math.min(idx2, idx3);
			if (idx4 != -1)
			{
				encoding = xml_data.substring(idx1 + 10, idx4);
			}
		}
		return writeTXT(file == null ? "file.xml" : file, xml_data, encoding, "text/xml");
	}

	/**
	 * Writes the given file to disk.
	 *
	 * If "file" is a JSFile or you are running in Smart Client, it writes data into a (local) binary file.
	 *
	 * If you are running in Web Client and "file" is a String (like 'mypdffile.pdf' to hint the browser what it is) the user will get
	 * prompted by the browser to save the file (sent using "Content-disposition: attachment" HTTP header). If it is a JSFile instance
	 * in this case it will be saved as a file on the server.
	 *
	 * @sample
	 * /**@type {Array<byte>}*&#47;
	 * var bytes = new Array();
	 * for (var i=0; i<1024; i++)
	 * 	bytes[i] = i % 100;
	 * var f = plugins.file.convertToJSFile('bin.dat');
	 * if (!plugins.file.writeFile(f, bytes))
	 * 	application.output('Failed to write the file.');
	 * // mimeType variable can be left null, and is used for webclient only. Specify one of any valid mime types as referenced here: https://developer.mozilla.org/en-US/docs/Properly_Configuring_Server_MIME_Types
	 * var mimeType = 'application/vnd.ms-excel'
	 * if (!plugins.file.writeFile(f, bytes, mimeType))
	 * 	application.output('Failed to write the file.');
	 *
	 * @param file a local JSFile
	 * @param data the data to be written
	 */
	public boolean js_writeFile(JSFile file, byte[] data)
	{
		return js_writeFile(file, data, null);
	}

	/**
	 * @clonedesc js_writeFile(JSFile,byte[])
	 * @sampleas js_writeFile(JSFile,byte[])
	 *
	 * @param file the file path as a String
	 * @param data the data to be written
	 */
	public boolean js_writeFile(String file, byte[] data)
	{
		return js_writeFile(file, data, null);
	}

	/**
	 * @clonedesc js_writeFile(JSFile, byte[])
	 * @sampleas js_writeFile(JSFile, byte[])
	 *
	 * @param file a local JSFile
	 * @param data the data to be written
	 * @param mimeType the mime type (used in Web-Client)
	 */
	public boolean js_writeFile(JSFile file, byte[] data, String mimeType)
	{
		return writeFile(file, data, mimeType);
	}

	/**
	 * @clonedesc js_writeFile(JSFile, byte[])
	 * @sampleas js_writeFile(JSFile, byte[])
	 *
	 * @param file the file path as a String
	 * @param data the data to be written
	 * @param mimeType the mime type (used in Web-Client)
	 */
	public boolean js_writeFile(String file, byte[] data, String mimeType)
	{
		return writeFile(file, data, mimeType);
	}

	protected boolean writeFile(Object f, byte[] data, @SuppressWarnings("unused") String mimeType)
	{
		if (data == null) return false;
		try
		{
			File file = getFileFromArg(f, true);
			if (file == null)
			{
				IClientPluginAccess access = plugin.getClientPluginAccess();
				IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
				Window currentWindow = null;
				if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
				file = FileChooserUtils.getAWriteFile(currentWindow, file, false);
			}
			if (file != null && !file.isDirectory())
			{
				FileOutputStream fos = new FileOutputStream(file);
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				bos.write(data);
				bos.close();
				fos.close();
				return true;
			}
			return false;
		}
		catch (Exception e)
		{
			Debug.error(e);
			return false;
		}
	}

	/**
	 * Opens the given local file.
	 *
	 * Smart Client: launches the default OS associated application to open an existing local file.
	 * Web Client: the (server local) file will open inside the browser - if supported (sent using "Content-disposition: inline" HTTP header).
	 *
	 * @param file the local file to open. The file should exist and be accessible.
	 * @return success status of the open operation
	 *
	 * @sample
	 * 	var myPDF = plugins.file.createFile('my.pdf');
	 *  myPDF.setBytes(data, true)
	 *	plugins.file.openFile(myPDF);
	 *
	 * @since 7.3
	 */
	public boolean js_openFile(JSFile file)
	{
		return js_openFile(file, null, null);
	}

	/**
	 * @clonedesc {@link #js_openFile(JSFile)}
	 *
	 * @param file the local file to open. The file should exist and be accessible.
	 * @param webClientTarget Target frame or named dialog/window. For example "_self" to open in the same browser window, "_blank" for another browser window. By default "_blank" is used.
	 * @param webClientTargetOptions window options used when a new browser window is to be shown; see browser JS 'window.open(...)' documentation.
	 * @return success status of the open operation
	 *
	 * @sample
	 * 	var myPDF = plugins.file.createFile('my.pdf');
	 *  myPDF.setBytes(data, true)
	 *	plugins.file.openFile(myPDF, "_self", null); // show in the same browser window
	 *
	 * @since 7.3.1
	 */
	public boolean js_openFile(JSFile file, String webClientTarget, String webClientTargetOptions)
	{
		try
		{
			Desktop.getDesktop().open(file.getFile());
			return true;
		}
		catch (Exception ex)
		{
			Debug.error(ex);
			return false;
		}
	}

	/**
	 * Opens the given data as a file.
	 *
	 * Smart Client: writes the data to a temporary file, then launches the default OS associated application to open it.
	 * Web Client: the data will open as a file inside the browser - if supported (sent using "Content-disposition: inline" HTTP header).
	 *
	 * @param fileName the name of the file that should open with the given data. Can be null (but in Smart Client null - so no extension - will probably make open fail).
	 * @param data the file's binary content.
	 * @param mimeType can be left null, and is used for webclient only. Specify one of any valid mime types:
	 * https://developer.mozilla.org/en-US/docs/Properly_Configuring_Server_MIME_Types
	 * http://www.iana.org/assignments/media-types/media-types.xhtml
	 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7
	 *
	 * @return success status of the open operation
	 *
	 * @sample
	 * // read or generate pdf file bytes
	 * var bytes = plugins.file.readFile("c:/ExportedPdfs/13542.pdf");
	 *
	 * // mimeType variable can be left null
	 * var mimeType = 'application/pdf'
	 *
	 * if (!plugins.file.openFile("MonthlyStatistics.pdf", bytes, mimeType))
	 * 	application.output('Failed to open the file.');
	 *
	 * @since 7.3.1
	 */
	public boolean js_openFile(String fileName, byte[] data, String mimeType)
	{
		return js_openFile(fileName, data, mimeType, null, null);
	}

	/**
	 * @clonedesc {@link #js_openFile(String, byte[], String)}
	 *
	 * @param fileName the name of the file that should open with the given data. Can be null (but in Smart Client null - so no extension - will probably make open fail).
	 * @param data the file's binary content.
	 * @param mimeType can be left null, and is used for webclient only. Specify one of any valid mime types:
	 * https://developer.mozilla.org/en-US/docs/Properly_Configuring_Server_MIME_Types
	 * http://www.iana.org/assignments/media-types/media-types.xhtml
	 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7
	 * @param webClientTarget Target frame or named dialog/window. For example "_self" to open in the same browser window, "_blank" for another browser window. By default "_blank" is used.
	 * @param webClientTargetOptions window options used when a new browser window is to be shown; see browser JS 'window.open(...)' documentation.
	 *
	 * @return success status of the open operation
	 *
	 * @sample
	 * // read or generate pdf file bytes
	 * var bytes = plugins.file.readFile("c:/ExportedPdfs/13542.pdf");
	 *
	 * // mimeType variable can be left null
	 * var mimeType = 'application/pdf'
	 *
	 * if (!plugins.file.openFile("MonthlyStatistics.pdf", bytes, mimeType, "_self", null))
	 * 	application.output('Failed to open the file.');
	 *
	 * @since 7.3.1
	 */
	public boolean js_openFile(String fileName, byte[] data, String mimeType, String webClientTarget, String webClientTargetOptions)
	{
		try
		{
			String suffix, prefix;
			if (fileName != null)
			{
				int idx = fileName.lastIndexOf('.');
				if (idx >= 0)
				{
					suffix = fileName.substring(idx);
					prefix = fileName.substring(0, idx);
				}
				else
				{
					suffix = null;
					prefix = fileName;
				}
			}
			else
			{
				suffix = "bin"; //$NON-NLS-1$
				prefix = "file"; //$NON-NLS-1$
			}
			JSFile file = js_createTempFile(prefix, suffix);
			js_writeFile(file, data);

			Desktop.getDesktop().open(file.getFile());
			return true;
		}
		catch (Exception ex)
		{
			Debug.error(ex);
			return false;
		}
	}

	/**
	 * Read all content from a text file. If a file name is not specified, then a file selection dialog pops up for selecting a file. The encoding can be also specified. (Web Enabled only for a JSFile argument)
	 *
	 * @sample
	 * // Read content from a known text file.
	 * var txt = plugins.file.readTXTFile('story.txt');
	 * application.output(txt);
	 * // Read content from a text file selected from the file open dialog.
	 * var txtUnknown = plugins.file.readTXTFile();
	 * application.output(txtUnknown);
	 */

	public String js_readTXTFile()
	{
		return js_readTXTFile((String)null, null);
	}

	/**
	 * @clonedesc js_readTXTFile()
	 * @sampleas js_readTXTFile()
	 *
	 * @param file JSFile.
	 */
	public String js_readTXTFile(JSFile file)
	{
		return js_readTXTFile(file, null);
	}

	/**
	 * @clonedesc js_readTXTFile()
	 * @sampleas js_readTXTFile()
	 *
	 * @param file the file path.
	 */
	public String js_readTXTFile(String file)
	{
		return js_readTXTFile(file, null);
	}

	/**
	 * @clonedesc js_readTXTFile()
	 * @sampleas js_readTXTFile()
	 *
	 * @param file JSFile.
	 * @param charsetname Charset name.
	 */
	public String js_readTXTFile(JSFile file, String charsetname)
	{
		return readTXTFile(file, charsetname);
	}

	/**
	 * @clonedesc js_readTXTFile()
	 * @sampleas js_readTXTFile()
	 *
	 * @param file the file path.
	 * @param charsetname Charset name.
	 */
	public String js_readTXTFile(String file, String charsetname)
	{
		return readTXTFile(file, charsetname);
	}

	private String readTXTFile(Object file, String charsetname)
	{
		try
		{
			File f = null;
			if (file != null)
			{
				f = getFileFromArg(file, true);
			}
			IClientPluginAccess access = plugin.getClientPluginAccess();
			IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
			Window currentWindow = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
			File fileObj = FileChooserUtils.getAReadFile(currentWindow, f, JFileChooser.FILES_ONLY, null);

			if (fileObj != null) // !cancelled
			{
				return readTXTFile(charsetname, new FileInputStream(fileObj));
			}
			return null;
		}
		catch (Exception e)
		{
			Debug.error(e);
			return null;
		}
	}

	/**
	 * @param args
	 * @param file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected String readTXTFile(String encoding, InputStream file) throws FileNotFoundException, IOException
	{
		Charset cs = null;
		if (encoding != null)
		{
			if (Charset.isSupported(encoding))
			{
				cs = Charset.forName(encoding);
			}
			else
			{
				return null;//unknown enc.
			}
		}

		InputStreamReader reader = null;
		if (cs != null)
		{
			reader = new InputStreamReader(file, cs);
		}
		else
		{
			reader = new InputStreamReader(file);//default char encoding
		}

		StringBuffer sb = new StringBuffer();
		BufferedReader br = new BufferedReader(reader);
		String line;
		while ((line = br.readLine()) != null)
		{
			sb.append(line);
			sb.append('\n');
		}
		sb.setLength(sb.length() - 1); // remove newline
		br.close();
		return sb.toString();
	}

	/**
	 * Shows a file save dialog. File save is only supported in the SmartClient.
	 *
	 * @sample
	 * var file = plugins.file.showFileSaveDialog();
	 * application.output("you've selected file: " + file.getAbsolutePath());
	 */
	@ServoyClientSupport(mc = false, wc = false, sc = true, ng = false)
	public JSFile js_showFileSaveDialog()
	{
		return js_showFileSaveDialog((String)null, null);
	}

	/**
	 * @clonedesc js_showFileSaveDialog()
	 * @sampleas js_showFileSaveDialog()
	 *
	 * @param fileNameDir JSFile to save.
	 */
	@ServoyClientSupport(mc = false, wc = false, sc = true, ng = false)
	public JSFile js_showFileSaveDialog(JSFile fileNameDir)
	{
		return js_showFileSaveDialog(fileNameDir, null);
	}

	/**
	 * @clonedesc js_showFileSaveDialog()
	 * @sampleas js_showFileSaveDialog()
	 *
	 * @param fileNameDir File (give as file path) to save.
	 */
	@ServoyClientSupport(mc = false, wc = false, sc = true, ng = false)
	public JSFile js_showFileSaveDialog(String fileNameDir)
	{
		return js_showFileSaveDialog(fileNameDir, null);
	}

	/**
	 * @clonedesc js_showFileSaveDialog()
	 * @sampleas js_showFileSaveDialog()
	 * @param fileNameDir JSFile to save
	 * @param title Dialog title.
	 */
	@ServoyClientSupport(mc = false, wc = false, sc = true, ng = false)
	public JSFile js_showFileSaveDialog(JSFile fileNameDir, String title)
	{
		return showFileSaveDialog(fileNameDir, title);
	}

	/**
	 * @clonedesc js_showFileSaveDialog()
	 * @sampleas js_showFileSaveDialog()
	 * @param fileNameDir File to save (specified as file path)
	 * @param title Dialog title.
	 */
	@ServoyClientSupport(mc = false, wc = false, sc = true, ng = false)
	public JSFile js_showFileSaveDialog(String fileNameDir, String title)
	{
		return showFileSaveDialog(fileNameDir, title);
	}

	private JSFile showFileSaveDialog(Object fileNameDir, String title)
	{
		File file = null;
		if (fileNameDir != null)
		{
			file = getFileFromArg(fileNameDir, true);
		}

		IClientPluginAccess access = plugin.getClientPluginAccess();
		if (access.getApplicationType() != IClientPluginAccess.CLIENT && access.getApplicationType() != IClientPluginAccess.RUNTIME)
			throw new UnsupportedMethodException("File save is only supported in the SmartClient (not in web or headless client)"); //$NON-NLS-1$
		IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
		Window currentWindow = null;
		if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
		File f = FileChooserUtils.getAWriteFile(currentWindow, file, true, title);
		if (f != null)
		{
			return new JSFile(f);
		}
		return null;
	}

	/**
	 * Shows a directory selector dialog.
	 *
	 * @sample
	 * var dir = plugins.file.showDirectorySelectDialog();
	 * application.output("you've selected folder: " + dir.getAbsolutePath());
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public JSFile js_showDirectorySelectDialog()
	{
		return js_showDirectorySelectDialog((String)null, null);
	}

	/**
	 * @clonedesc js_showDirectorySelectDialog()
	 * @sampleas js_showDirectorySelectDialog()
	 *
	 * @param directory Default directory as JSFile.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public JSFile js_showDirectorySelectDialog(JSFile directory)
	{
		return js_showDirectorySelectDialog(directory, null);
	}

	/**
	 * @clonedesc js_showDirectorySelectDialog()
	 * @sampleas js_showDirectorySelectDialog()
	 *
	 * @param directory Default directory as file path.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public JSFile js_showDirectorySelectDialog(String directory)
	{
		return js_showDirectorySelectDialog(directory, null);
	}

	/**
	 * @clonedesc js_showDirectorySelectDialog()
	 * @sampleas js_showDirectorySelectDialog()
	 *
	 * @param directory Default directory as JSFile.
	 * @param title Dialog title.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public JSFile js_showDirectorySelectDialog(JSFile directory, String title)
	{
		return showDirectorySelectDialog(directory, title);
	}

	/**
	 * @clonedesc js_showDirectorySelectDialog()
	 * @sampleas js_showDirectorySelectDialog()
	 *
	 * @param directory Default directory as file path.
	 * @param title Dialog title.
	 */
	@ServoyClientSupport(ng = false, wc = false, sc = true)
	public JSFile js_showDirectorySelectDialog(String directory, String title)
	{
		return showDirectorySelectDialog(directory, title);
	}

	private JSFile showDirectorySelectDialog(Object directory, String title)
	{
		File f = null;
		if (directory != null)
		{
			f = getFileFromArg(directory, true);
		}

		IClientPluginAccess access = plugin.getClientPluginAccess();
		if (access.getApplicationType() != IClientPluginAccess.CLIENT && access.getApplicationType() != IClientPluginAccess.RUNTIME)
			throw new UnsupportedMethodException("Directory selection is only supported in the SmartClient (not in web or headless client)"); //$NON-NLS-1$
		IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
		Window currentWindow = null;
		if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
		File retval = FileChooserUtils.getAReadFile(currentWindow, f, JFileChooser.DIRECTORIES_ONLY, null, title);
		if (retval != null)
		{
			return new JSFile(retval);
		}
		return null;
	}

	/**
	 * Reads all or part of the content from a binary file. If a file name is not specified, then a file selection dialog pops up for selecting a file. (Web Enabled only for a JSFile argument)
	 *
	 * @sample
	 * // Read all content from the file.
	 * var bytes = plugins.file.readFile('big.jpg');
	 * application.output('file size: ' + bytes.length);
	 * // Read only the first 1KB from the file.
	 * var bytesPartial = plugins.file.readFile('big.jpg', 1024);
	 * application.output('partial file size: ' + bytesPartial.length);
	 * // Read all content from a file selected from the file open dialog.
	 * var bytesUnknownFile = plugins.file.readFile();
	 * application.output('unknown file size: ' + bytesUnknownFile.length);
	 *
	 */
	public byte[] js_readFile()
	{
		return js_readFile((String)null, -1);
	}

	/**
	 * @clonedesc js_readFile()
	 * @sampleas js_readFile()
	 *
	 * @param file JSFile.
	 */
	public byte[] js_readFile(JSFile file)
	{
		return js_readFile(file, -1);
	}

	/**
	 * @clonedesc js_readFile()
	 * @sampleas js_readFile()
	 *
	 * @param file the file path.
	 */
	public byte[] js_readFile(String file)
	{
		return js_readFile(file, -1);
	}

	/**
	 * @clonedesc js_readFile()
	 * @sampleas js_readFile()
	 *
	 * @param file JSFile.
	 * @param size Number of bytes to read.
	 */
	public byte[] js_readFile(JSFile file, long size)
	{
		return readFile(file, size);
	}

	/**
	 * @clonedesc js_readFile()
	 * @sampleas js_readFile()
	 *
	 * @param file the file path.
	 * @param size Number of bytes to read.
	 */
	public byte[] js_readFile(String file, long size)
	{
		return readFile(file, size);
	}

	private byte[] readFile(Object file, long size)
	{
		try
		{
			File f = null;
			if (file != null)
			{
				f = getFileFromArg(file, true);
			}

			IClientPluginAccess access = plugin.getClientPluginAccess();
			IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
			Window currentWindow = null;
			if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
			File fileObj = FileChooserUtils.getAReadFile(currentWindow, f, JFileChooser.FILES_ONLY, null);

			byte[] retval = null;
			if (fileObj != null && fileObj.exists() && !fileObj.isDirectory()) // !cancelled
			{
				if (SwingUtilities.isEventDispatchThread())
				{
					retval = FileChooserUtils.paintingReadFile(access.getExecutor(), access, fileObj, size);
				}
				else
				{
					retval = FileChooserUtils.readFile(fileObj, size);
				}
			}
			return retval;
		}
		catch (Exception e)
		{
			Debug.error(e);
			return null;
		}
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		return new Class[] { JSFile.class, JSProgressMonitor.class };
	}


	/**
	 * Appends a string given in parameter to a file, using default platform encoding.
	 *
	 * @sample
	 * // append some text to a text file:
	 * 	var ok = plugins.file.appendToTXTFile('myTextFile.txt', '\nMy fantastic new line of text\n');
	 *
	 * @since Servoy 5.2
	 *
	 * @param file a local {@link JSFile}
	 * @param text the text to append to the file
	 * @return true if appending worked
	 */
	public boolean js_appendToTXTFile(JSFile file, String text)
	{
		return js_appendToTXTFile(file, text, null);
	}

	/**
	 * @clonedesc js_appendToTXTFile(JSFile,String)
	 * @sampleas js_appendToTXTFile(JSFile,String)
	 *
	 * @since Servoy 5.2
	 *
	 * @param file the file path as a String
	 * @param text the text to append to the file
	 * @return true if appending worked
	 */
	public boolean js_appendToTXTFile(String file, String text)
	{
		return js_appendToTXTFile(file, text, null);
	}

	/**
	 * Appends a string given in parameter to a file, using the specified encoding.
	 *
	 * @sampleas js_appendToTXTFile(JSFile, String)
	 *
	 * @since Servoy 5.2
	 *
	 * @param file a local {@link JSFile}
	 * @param text the text to append to the file
	 * @param encoding the encoding to use
	 *
	 * @return true if appending worked
	 */
	public boolean js_appendToTXTFile(JSFile file, String text, String encoding)
	{
		return appendToTXTFile(file, text, encoding);
	}

	/**
	 * @clonedesc js_appendToTXTFile(JSFile,String,String)
	 * @sampleas js_appendToTXTFile(JSFile,String,String)
	 *
	 * @param file the file path as a String
	 * @param text the text to append to the file
	 * @param encoding the encoding to use
	 */
	public boolean js_appendToTXTFile(String file, String text, String encoding)
	{
		return appendToTXTFile(file, text, encoding);
	}

	@SuppressWarnings("nls")
	private boolean appendToTXTFile(Object file, String text, String encoding)
	{
		if (text != null)
		{
			try
			{
				final IClientPluginAccess access = plugin.getClientPluginAccess();
				File f = getFileFromArg(file, true);
				if (f == null)
				{
					IRuntimeWindow runtimeWindow = access.getCurrentRuntimeWindow();
					Window currentWindow = null;
					if (runtimeWindow instanceof ISmartRuntimeWindow) currentWindow = ((ISmartRuntimeWindow)runtimeWindow).getWindow();
					f = FileChooserUtils.getAWriteFile(currentWindow, f, false);
				}
				FileOutputStream fos = new FileOutputStream(f, true);
				try
				{
					return writeToOutputStream(fos, text.replaceAll("\\n", LF), encoding);
				}
				finally
				{
					fos.close();
				}
			}
			catch (final Exception ex)
			{
				Debug.error(ex);
			}
		}
		return false;
	}

	/**
	 * Convenience return to get a JSFile representation of a server file based on its path.
	 *
	 * @sample
	 * var f = plugins.file.convertToRemoteJSFile('/story.txt');
	 * if (f && f.canRead())
	 * 	application.output('File can be read.');
	 *
	 * @since Servoy 5.2
	 *
	 * @param path the path representing a file on the server (should start with "/")
	 * @return the {@link JSFile}
	 */
	@SuppressWarnings("nls")
	public JSFile js_convertToRemoteJSFile(final String path)
	{
		if (path == null)
		{
			throw new IllegalArgumentException("Server path cannot be null");
		}
		if (path.charAt(0) != '/')
		{
			throw new IllegalArgumentException("Remote path should start with '/'");
		}
		try
		{
			final IFileService service = getFileService();
			final String clientId = plugin.getClientPluginAccess().getClientID();
			final RemoteFileData data = service.getRemoteFileData(clientId, path);
			return new JSFile(new RemoteFile(data, service, clientId));
		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}
		return null;
	}

	/**
	 * Returns an array of JSFile instances corresponding to content of the specified folder on the server side. The content can be filtered by optional name filter(s), by type, by visibility and by lock status.
	 *
	 * @sample
	 * // retrieves an array of files located on the server side inside the default upload folder:
	 * var files = plugins.file.getRemoteFolderContents(plugins.file.convertToRemoteJSFile('/'), '.txt');
	 *
	 * @since Servoy 5.2.1
	 *
	 * @param targetFolder
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(JSFile targetFolder)
	{
		return js_getRemoteFolderContents(targetFolder, null, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * Returns an array of JSFile instances corresponding to content of the specified folder on the server side. The content can be filtered by optional name filter(s), by type, by visibility and by lock status.
	 *
	 * @sample
	 * // retrieves an array of files located on the server side inside the default upload folder:
	 * var files = plugins.file.getRemoteFolderContents('/', '.txt');
	 *
	 * @since Servoy 5.2.1
	 *
	 * @param targetFolder
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(String targetFolder)
	{
		return js_getRemoteFolderContents(targetFolder, null, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(JSFile)
	 * @sampleas js_getRemoteFolderContents(JSFile)
	 * @param targetFolder Folder as JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(JSFile targetFolder, Object fileFilter)
	{
		return js_getRemoteFolderContents(targetFolder, fileFilter, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(String)
	 * @sampleas js_getRemoteFolderContents(String)
	 * @param targetFolder Folder path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(String targetFolder, Object fileFilter)
	{
		return js_getRemoteFolderContents(targetFolder, fileFilter, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(JSFile)
	 * @sampleas js_getRemoteFolderContents(JSFile)
	 * @param targetFolder Folder as JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(JSFile targetFolder, Object fileFilter, Number fileOption)
	{
		return js_getRemoteFolderContents(targetFolder, fileFilter, fileOption, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(String)
	 * @sampleas js_getRemoteFolderContents(String)
	 * @param targetFolder Folder path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(String targetFolder, Object fileFilter, Number fileOption)
	{
		return js_getRemoteFolderContents(targetFolder, fileFilter, fileOption, AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(JSFile)
	 * @sampleas js_getRemoteFolderContents(JSFile)
	 * @param targetFolder Folder as JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(JSFile targetFolder, Object fileFilter, Number fileOption, Number visibleOption)
	{
		return js_getRemoteFolderContents(targetFolder, fileFilter, fileOption, visibleOption, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(String)
	 * @sampleas js_getRemoteFolderContents(String)
	 * @param targetFolder Folder path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(String targetFolder, Object fileFilter, Number fileOption, Number visibleOption)
	{
		return js_getRemoteFolderContents(targetFolder, fileFilter, fileOption, visibleOption, AbstractFile.ALL_INTEGER);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(JSFile)
	 * @sampleas js_getRemoteFolderContents(JSFile)
	 * @param targetFolder Folder as JSFile object.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 * @param lockedOption 1=locked, 2=nonlocked
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(JSFile targetFolder, Object fileFilter, Number fileOption, Number visibleOption, Number lockedOption)
	{
		return getRemoteFolderContents(targetFolder, fileFilter, fileOption, visibleOption, lockedOption);
	}

	/**
	 * @clonedesc js_getRemoteFolderContents(String)
	 * @sampleas js_getRemoteFolderContents(String)
	 * @param targetFolder Folder path.
	 * @param fileFilter Filter or array of filters for files in folder.
	 * @param fileOption 1=files, 2=dirs
	 * @param visibleOption 1=visible, 2=nonvisible
	 * @param lockedOption 1=locked, 2=nonlocked
	 * @return the array of file names
	 */
	public JSFile[] js_getRemoteFolderContents(String targetFolder, Object fileFilter, Number fileOption, Number visibleOption, Number lockedOption)
	{
		return getRemoteFolderContents(targetFolder, fileFilter, fileOption, visibleOption, lockedOption);
	}

	@SuppressWarnings("nls")
	private JSFile[] getRemoteFolderContents(Object targetFolder, Object fileFilter, Number fileOption, Number visibleOption, Number lockedOption)
	{
		final int _fileOption = getNumberAsInt(fileOption, AbstractFile.ALL);
		final int _visibleOption = getNumberAsInt(visibleOption, AbstractFile.ALL);
		final int _lockedOption = getNumberAsInt(lockedOption, AbstractFile.ALL);

		if (targetFolder == null) return EMPTY;

		String[] fileFilterA = null;
		if (fileFilter != null)
		{
			if (fileFilter instanceof String[])
			{
				fileFilterA = (String[])fileFilter;
				for (int i = 0; i < fileFilterA.length; i++)
				{
					fileFilterA[i] = fileFilterA[i].toLowerCase();
				}
			}
			else
			{
				fileFilterA = new String[] { fileFilter.toString().toLowerCase() };
			}
		}

		String serverFileName = null;
		if (targetFolder instanceof JSFile)
		{
			IAbstractFile abstractFile = ((JSFile)targetFolder).getAbstractFile();
			if (abstractFile instanceof RemoteFile)
			{
				serverFileName = ((RemoteFile)abstractFile).getAbsolutePath();
			}
			else
			{
				throw new IllegalArgumentException("Local file path doesn't make sense for the getRemoteDirList method");
			}
		}
		else
		{
			serverFileName = targetFolder.toString();
		}
		try
		{
			final IFileService service = getFileService();
			final String clientId = plugin.getClientPluginAccess().getClientID();
			final RemoteFileData[] remoteList = service.getRemoteFolderContent(clientId, serverFileName, fileFilterA, _fileOption, _visibleOption,
				_lockedOption);
			if (remoteList != null)
			{
				final JSFile[] files = new JSFile[remoteList.length];
				for (int i = 0; i < files.length; i++)
				{
					files[i] = new JSFile(new RemoteFile(remoteList[i], service, clientId));
				}
				return files;
			}
			else
			{
				return EMPTY;
			}

		}
		catch (Exception ex)
		{
			Debug.error(ex);
		}

		return null;
	}

	/**
	 * Retrieves an array of files/folders from the server
	 *
	 * @deprecated Replaced by {@link #getRemoteFolderContents(Object[])}.
	 *
	 * @since Servoy 5.2
	 *
	 * @param serverPath the path of a remote directory (relative to the defaultFolder)
	 *
	 * @return the array of file names
	 */
	@Deprecated
	public JSFile[] js_getRemoteList(final Object serverPath)
	{
		return js_getRemoteList(serverPath, false);
	}

	/**
	 * Retrieves an array of files/folders from the server
	 *
	 * @deprecated Replaced by {@link #getRemoteFolderContents(Object[])}.
	 *
	 * @since Servoy 5.2
	 *
	 * @param serverPath a {@link JSFile} or String with the path of a remote directory (relative to the defaultFolder)
	 * @param filesOnly if true only files will be retrieve, if false, files and folders will be retrieved
	 *
	 * @return the array of file names
	 */
	@Deprecated
	public JSFile[] js_getRemoteList(final Object serverPath, final boolean filesOnly)
	{
		final int fileOption = (filesOnly) ? AbstractFile.FILES : AbstractFile.ALL;
		return getRemoteFolderContents(serverPath, null, (new Integer(fileOption)), AbstractFile.ALL_INTEGER, AbstractFile.ALL_INTEGER);
	}

	/**
	 * Overloaded method, only defines file(s) to be streamed
	 *
	 * @since Servoy 5.2
	 *
	 * @sample
	 * // send one file:
	 * var file = plugins.file.showFileOpenDialog( 1, null, false, null, null, 'Choose a file to transfer' );
	 * if (file) {
	 * 	plugins.file.streamFilesToServer( file, callbackFunction );
	 * }
	 * //plugins.file.streamFilesToServer( 'servoy.txt', callbackFunction );
	 *
	 * // send an array of files:
	 * var folder = plugins.file.showDirectorySelectDialog();
	 * if (folder) {
	 * 	var files = plugins.file.getFolderContents(folder);
	 * 	if (files) {
	 * 		var monitor = plugins.file.streamFilesToServer( files, callbackFunction );
	 * 	}
	 * }
	 * // var files = new Array()
	 * // files[0] = 'file1.txt';
	 * // files[1] = 'file2.txt';
	 * // var monitor = plugins.file.streamFilesToServer( files, callbackFunction );
	 *
	 * @param files file(s) to be streamed (can be a String path or a {@link JSFile}) or an Array of these
	 * @return a {@link JSProgressMonitor} object to allow client to subscribe to progress notifications
	 */
	public JSProgressMonitor js_streamFilesToServer(final Object files)
	{
		return js_streamFilesToServer(files, null, null);
	}

	/**
	 * Overloaded method, defines file(s) to be streamed and a callback function
	 *
	 * @since Servoy 5.2
	 *
	 * @sampleas js_streamFilesToServer(Object)
	 *
	 * @param files file(s) to be streamed (can be a String path or a {@link JSFile}) or an Array of these
	 * @param serverFiles can be a JSFile or JSFile[], a String or String[], representing the file name(s) to use on the server
	 * @return a {@link JSProgressMonitor} object to allow client to subscribe to progress notifications
	 */
	public JSProgressMonitor js_streamFilesToServer(final Object files, final Object serverFiles)
	{
		return js_streamFilesToServer(files, serverFiles, null);
	}

	/**
	 * Overloaded method, defines file(s) to be streamed and a callback function
	 *
	 * @since Servoy 5.2
	 *
	 * @sampleas js_streamFilesToServer(Object)
	 *
	 * @param files file(s) to be streamed (can be a String path or a {@link JSFile}) or an Array of these
	 * @param callback the {@link Function} to be called back at the end of the process (for every file); the callback function is invoked with argument the filename that was transfered; an extra second exception parameter can be given if an exception occured
	 * @return a {@link JSProgressMonitor} object to allow client to subscribe to progress notifications
	 */
	public JSProgressMonitor js_streamFilesToServer(final Object files, final Function callback)
	{
		return js_streamFilesToServer(files, null, callback);
	}

	/**
	 * Overloaded method, defines file(s) to be streamed, a callback function and file name(s) to use on the server
	 *
	 * @since Servoy 5.2
	 *
	 * @sampleas js_streamFilesToServer(Object)
	 *
	 * @param files file(s) to be streamed (can be a String path or a {@link JSFile}) or an Array of these)
	 * @param serverFiles can be a JSFile or JSFile[], a String or String[], representing the file name(s) to use on the server
	 * @param callback the {@link Function} to be called back at the end of the process (for every file); the callback function is invoked with argument the filename that was transfered; an extra second exception parameter can be given if an exception occured
	 * @return a {@link JSProgressMonitor} object to allow client to subscribe to progress notifications
	 */
	public JSProgressMonitor js_streamFilesToServer(final Object files, final Object serverFiles, final Function callback)
	{
		if (files != null)
		{
			final Object[] fileObjects = unwrap(files);
			final Object[] serverObjects = unwrap(serverFiles);
			if (fileObjects != null)
			{
				// the FunctionDefinition is only created once for all files:
				final FunctionDefinition function = (callback == null) ? null : new FunctionDefinition(callback);
				final File[] filesToBeStreamed = new File[fileObjects.length];
				long totalBytes = 0;
				for (int i = 0; i < fileObjects.length; i++)
				{
					final File file = getFileFromArg(fileObjects[i], true);
					if (file != null && file.canRead())
					{
						totalBytes += file.length();
						filesToBeStreamed[i] = file;
					}
					else if (fileObjects[i] instanceof JSFile)
					{
						IAbstractFile af = ((JSFile)fileObjects[i]).getAbstractFile();
						if (af instanceof UploadData)
						{
							throw new RuntimeException(
								"Using streamFilesToServer with an uploadData in the web client makes no sense since the process is already on the server-side, consider using writeFile(), writeTXTFile() or writeXMLFile() instead!"); //$NON-NLS-1$
						}
						filesToBeStreamed[i] = null;
					}
				}
				final JSProgressMonitor progressMonitor = new JSProgressMonitor(this, totalBytes, filesToBeStreamed.length);
				try
				{
					final IFileService service = getFileService();
					plugin.getClientPluginAccess().getExecutor().execute(
						new ToServerWorker(filesToBeStreamed, serverObjects, function, progressMonitor, service));

					return progressMonitor;
				}
				catch (Exception ex)
				{
					Debug.error(ex);
				}

			}
		}
		return null;
	}

	/**
	 * Stream 1 or more files from the server to the client.
	 *
	 * @since Servoy 5.2
	 *
	 * @sample
	 * // transfer all the files of a chosen server folder to a directory on the client
	 * var dir = plugins.file.showDirectorySelectDialog();
	 * if (dir) {
	 * 	var list = plugins.file.getRemoteFolderContents('/images/user1/', null, 1);
	 * 	if (list) {
	 * 		var monitor = plugins.file.streamFilesFromServer(dir, list, callbackFunction);
	 * 	}
	 * }
	 *
	 * // transfer one file on the client
	 * var monitor = plugins.file.streamFilesFromServer('/path/to/file', 'path/to/serverFile', callbackFunction);
	 *
	 * // transfer an array of serverFiles to an array of files on the client
	 * var files = new Array();
	 * files[0] = '/path/to/file1';
	 * files[1] = '/path/to/file2';
	 * var serverFiles = new Array();
	 * serverFiles[0] = '/path/to/serverFile1';
	 * serverFiles[1] = '/path/to/serverFile2';
	 * var monitor = plugins.file.streamFilesFromServer(files, serverFiles, callbackFunction);
	 *
	 * @param files file(s) to be streamed into (can be a String path a {@link JSFile}) or an Array of these
	 * @param serverFiles the files on the server that will be transfered to the client, can be a String or a String[]
	 * @return a {@link JSProgressMonitor} object to allow client to subscribe to progress notifications
	 */
	public JSProgressMonitor js_streamFilesFromServer(final Object files, final Object serverFiles)
	{
		return js_streamFilesFromServer(files, serverFiles, null);
	}

	/**
	 * Stream 1 or more files from the server to the client, the callback method is invoked after every file, with as argument
	 * the filename that was transfered. An extra second exception parameter can be given if an exception did occur.
	 *
	 * @since Servoy 5.2
	 *
	 * @sampleas js_streamFilesFromServer(Object, Object)
	 *
	 * @param files file(s) to be streamed into (can be a String path or a {@link JSFile}) or an Array of these
	 * @param serverFiles the files on the server that will be transfered to the client, can be a JSFile or JSFile[], a String or String[]
	 * @param callback the {@link Function} to be called back at the end of the process (for every file); the callback function is invoked with argument the filename that was transfered; an extra second exception parameter can be given if an exception occured
	 * @return a {@link JSProgressMonitor} object to allow client to subscribe to progress notifications
	 */
	@SuppressWarnings("nls")
	public JSProgressMonitor js_streamFilesFromServer(final Object files, final Object serverFiles, final Function callback)
	{
		if (files != null && serverFiles != null)
		{
			final Object[] fileObjects = unwrap(files);
			final Object[] serverObjects = unwrap(serverFiles);
			if (fileObjects != null && serverObjects != null && serverObjects.length > 0)
			{
				final File firstFile = getFileFromArg(fileObjects[0], true);
				if (fileObjects.length != serverObjects.length)
				{
					// we may have a folder, but then it must be a single argument:
					if (fileObjects.length == 1)
					{
						if (!firstFile.isDirectory())
						{
							throw new IllegalArgumentException(
								"The first argument must represent an existing folder or an array of files to receive the server files.");
						}
					}
					else
					{
						throw new IllegalArgumentException("The number of files on the client side and on the server side don't match.");
					}
				}
				// the FunctionDefinition is only created once for all files:
				final FunctionDefinition function = (callback == null) ? null : new FunctionDefinition(callback);
				final File[] filesToBeStreamed = new File[serverObjects.length];
				long totalBytes = 0;

				try
				{
					final IFileService service = getFileService();
					final String clientId = plugin.getClientPluginAccess().getClientID();
					final RemoteFile[] remoteFiles = new RemoteFile[serverObjects.length];
					if (serverObjects[0] instanceof JSFile)
					{
						for (int i = 0; i < serverObjects.length; i++)
						{
							Object serverFile = serverObjects[i];
							if (serverFile != null)
							{
								IAbstractFile abstractFile = ((JSFile)serverFile).getAbstractFile();
								if (abstractFile instanceof RemoteFile)
								{
									remoteFiles[i] = (RemoteFile)abstractFile;
								}
								else
								{
									throw new IllegalArgumentException("Wrong file type provided: the JSFile to transfer must be a remote file!");
								}
							}
							if (remoteFiles[i] != null)
							{
								totalBytes += remoteFiles[i].size();
								// we can have a related local file, else a folder has been provided, thus we create a related local file to receive the transfer:
								filesToBeStreamed[i] = (i < fileObjects.length && !firstFile.isDirectory()) ? getFileFromArg(fileObjects[i], true)
									: new File(firstFile, remoteFiles[i].getName());
							}
						}
					}
					else
					{
						String[] serverFileNames = new String[serverObjects.length];
						for (int i = 0; i < serverObjects.length; i++)
						{
							serverFileNames[i] = serverObjects[i].toString();
						}
						RemoteFileData[] datas = service.getRemoteFileData(clientId, serverFileNames);
						for (int i = 0; i < datas.length; i++)
						{
							remoteFiles[i] = new RemoteFile(datas[i], service, clientId);
							totalBytes += datas[i].size();
							filesToBeStreamed[i] = (i < fileObjects.length && !firstFile.isDirectory()) ? getFileFromArg(fileObjects[i], true)
								: new File(firstFile, remoteFiles[i].getName());
						}
					}
					JSProgressMonitor progressMonitor = new JSProgressMonitor(this, totalBytes, remoteFiles.length);
					plugin.getClientPluginAccess().getExecutor().execute(
						new FromServerWorker(filesToBeStreamed, remoteFiles, function, progressMonitor, service));
					return progressMonitor;
				}
				catch (Exception ex)
				{
					Debug.error(ex);
				}
			}
		}
		return null;
	}

	/**
	 * Utility method to give access to the {@link IFileService} remote service
	 * @since Servoy 5.2
	 *
	 * @return the service
	 */
	protected IFileService getFileService() throws Exception
	{
		return (IFileService)plugin.getClientPluginAccess().getRemoteService(IFileService.class.getName());
	}

	/**
	 * Utility method to unwrap a given object to Object[] array
	 * @since Servoy 5.2
	 *
	 * @param f The object to unwrap
	 * @return The Object[] array
	 */
	protected Object[] unwrap(Object f)
	{
		Object[] files = null;
		if (f != null)
		{
			if (f instanceof NativeArray)
			{
				files = (Object[])((NativeArray)f).unwrap();
			}
			else if (f instanceof Object[])
			{
				return (Object[])f;
			}
			else
			{
				files = new Object[] { f };
			}
		}
		return files;

	}

	/**
	 * Schedule a JSProgressMonitor to be run at fixed interval
	 *
	 * @param monitor the {@link JSProgressMonitor} to schedule
	 * @param interval the interval (in seconds) to run the callback
	 */
	public void scheduleMonitor(final JSProgressMonitor monitor, final float interval)
	{
		long delay = Math.round(interval * 1000);
		timer.scheduleAtFixedRate(monitor, 0L, delay);
	}


	/**
	 * Callback a Servoy {@link Function} passing a JSProgressMonitor
	 *
	 * @param monitor the {@link JSProgressMonitor} to return to the client
	 * @param function the client {@link FunctionDefinition} of a Servoy {@link Function} to callback
	 */
	public void callbackProgress(final JSProgressMonitor monitor, final FunctionDefinition function)
	{
		if (function != null)
		{
			function.executeAsync(plugin.getClientPluginAccess(), new Object[] { monitor });
		}
	}

	/**
	 * Returns the default upload location path of the server.
	 *
	 * @sample
	 * // get the (server-side) default upload location path:
	 * var serverPath = plugins.file.getDefaultUploadLocation();
	 *
	 * @return the location as canonical path
	 */
	public String js_getDefaultUploadLocation()
	{
		try
		{
			final IFileService service = getFileService();
			return service.getDefaultFolderLocation(plugin.getClientPluginAccess().getClientID());
		}
		catch (final Exception ex)
		{
			Debug.error(ex);
		}
		return null;
	}

	/**
	 * Get a url from a remote file that can be used to download the file in a browser.
	 * This is a complete url with the server url that is get from application.getServerURL()
	 *
	 * @sample
	 * var file = plugins.file.convertToRemoteJSFile("aremotefile.pdf");
	 * var url = plugins.file.getUrlForRemoteFile(file);
	 * application.showURL(url);
	 *
	 * @param file the remote file where the url should be generated from. Must be a remote file
	 * @return the url as a string
	 */
	public String js_getUrlForRemoteFile(JSFile file) throws Exception
	{
		if (file.getAbstractFile() instanceof RemoteFile)
		{
			if (!file.js_exists()) throw new RuntimeException("File " + file.js_getName() + " does not exist on the server");
			URL serverURL = plugin.getClientPluginAccess().getServerURL();
			return new URL(serverURL.toURI().toString() + "/servoy-service/file" + file.js_getAbsolutePath()).toURI().toString(); //$NON-NLS-1$
		}
		throw new RuntimeException("File " + file.js_getName() + " is not a remote file");
	}

	/**
	 * @clonedesc js_getUrlForRemoteFile(JSFile)
	 * @sampleas js_getUrlForRemoteFile(JSFile)
	 *
	 * @param file the remote file where the url should be generated from. Must be a remote file
	 * @return the url as a string
	 */
	public String js_getUrlForRemoteFile(String file) throws Exception
	{
		return js_getUrlForRemoteFile(js_convertToRemoteJSFile(file));
	}

	/**
	 * If the client's solution is closed, the file given to this method wil be deleted.
	 * This can be a remote or local file.
	 *
	 * This can be used to have temp files within a client that will be cleaned up when the solution is closed.
	 * So they live as long as the client has its solution open.
	 *
	 * @sample
	 * var file = plugins.file.createFile("newfile.txt");
	 * plugins.file.writeTXTFile(file, "some data");
	 * plugins.file.trackFileForDeletionfile(file);
	 *
	 * @param file the file to track
	 */
	public void js_trackFileForDeletion(JSFile file)
	{
		trackedFiles.add(file);
	}

	void deleteTrackedFiles()
	{
		for (JSFile file : trackedFiles)
		{
			if (!file.js_deleteFile())
			{
				Debug.log("File: " + file.js_getName() + " that was tracked for deletion didn't delete");
			}
		}
		trackedFiles.clear();
	}


	public void unload()
	{
		if (timer != null)
		{
			timer.cancel();
		}
	}

	private final class FromServerWorker implements Runnable
	{
		private final File[] files;
		private final RemoteFile[] remoteFiles;
		private final FunctionDefinition function;
		private final JSProgressMonitor progressMonitor;
		private final IFileService service;

		/**
		 * @param files
		 * @param remoteFiles
		 * @param function
		 * @param progressMonitor
		 * @param service
		 */
		public FromServerWorker(final File[] files, RemoteFile[] remoteFiles, final FunctionDefinition function, final JSProgressMonitor progressMonitor,
			final IFileService service)
		{
			this.files = files;
			this.remoteFiles = remoteFiles;
			this.function = function;
			this.progressMonitor = progressMonitor;
			this.service = service;
		}

		public void run()
		{
			try
			{
				long totalTransfered = 0L;
				final String clientId = plugin.getClientPluginAccess().getClientID();
				for (int i = 0; i < files.length; i++)
				{
					UUID uuid = null;
					OutputStream os = null;
					Exception ex = null;
					File file = files[i];
					if (file != null)
					{
						RemoteFile remote = remoteFiles[i];
						try
						{
							if (file.exists() || file.createNewFile())
							{
								long currentTransferred = 0L;
								progressMonitor.setCurrentFileName(remote.getAbsolutePath());
								progressMonitor.setCurrentBytes(remote.size());
								progressMonitor.setCurrentFileIndex(i + 1);
								progressMonitor.setCurrentTransferred(0L);

								os = new FileOutputStream(file);
								uuid = service.openTransfer(clientId, remote.getAbsolutePath());
								if (uuid != null)
								{
									byte[] bytes = service.readBytes(uuid, CHUNK_BUFFER_SIZE);
									while (bytes != null && !progressMonitor.js_isCanceled())
									{
										os.write(bytes);
										totalTransfered += bytes.length;
										currentTransferred += bytes.length;
										progressMonitor.setTotalTransferred(totalTransfered);
										progressMonitor.setCurrentTransferred(currentTransferred);
										if (progressMonitor.getDelay() > 0)
										{
											Thread.sleep(progressMonitor.getDelay()); // to test the process
										}
										// check for the length (this results in 1 less call to the server)
										if (bytes.length == CHUNK_BUFFER_SIZE)
										{
											bytes = service.readBytes(uuid, CHUNK_BUFFER_SIZE);
										}
										else break;
									}
								}
							}
						}
						catch (final Exception e)
						{
							Debug.error(e);
							ex = e;
						}
						finally
						{
							try
							{
								if (uuid != null) service.closeTransfer(uuid);
							}
							catch (final RemoteException ignore)
							{
							}
							try
							{
								if (os != null) os.close();
							}
							catch (final IOException ignore)
							{
							}
							if (function != null && !progressMonitor.js_isCanceled())
							{
								function.executeAsync(plugin.getClientPluginAccess(), new Object[] { new JSFile(file), ex });
							}
							if (progressMonitor.js_isCanceled())
							{
								file.delete();
								progressMonitor.run();
								break;
							}
						}
					}
				}
			}
			finally
			{
				if (!progressMonitor.js_isCanceled())
				{
					progressMonitor.setFinished(true);
					progressMonitor.run();
				}
				progressMonitor.cancel(); // stops the TimerTask
			}
		}
	}

	private final class ToServerWorker implements Runnable
	{

		private final File[] files;
		private final Object[] serverFiles;
		private final FunctionDefinition function;
		private final JSProgressMonitor progressMonitor;
		private final IFileService service;

		/**
		 * @param files
		 * @param serverFiles
		 * @param function
		 * @param progressMonitor
		 * @param service
		 */
		public ToServerWorker(final File[] files, final Object[] serverFiles, final FunctionDefinition function, final JSProgressMonitor progressMonitor,
			final IFileService service)
		{
			this.files = files;
			this.serverFiles = serverFiles;
			this.function = function;
			this.progressMonitor = progressMonitor;
			this.service = service;
		}

		public void run()
		{
			try
			{
				long totalTransfered = 0L;
				for (int i = 0; i < files.length; i++)
				{
					final File file = files[i];
					if (file != null)
					{
						// the serverName can be derived from an Array of String, at the same index as the file
						String serverFileName = null;
						if (serverFiles != null && i < serverFiles.length)
						{
							if (serverFiles[i] instanceof JSFile)
							{
								JSFile jsFile = (JSFile)serverFiles[i];
								IAbstractFile abstractFile = jsFile.getAbstractFile();
								if (abstractFile instanceof RemoteFile)
								{
									serverFileName = ((RemoteFile)abstractFile).getAbsolutePath();
								}
								else
								{
									serverFileName = abstractFile.getName();
								}
							}
							else
							{
								serverFileName = serverFiles[i].toString();
							}
						}
						else
						{
							serverFileName = "/" + file.getName(); //$NON-NLS-1$
						}

						long currentTransferred = 0L;
						progressMonitor.setCurrentFileName(file.getAbsolutePath());
						progressMonitor.setCurrentBytes(file.length());
						progressMonitor.setCurrentFileIndex(i + 1);
						progressMonitor.setCurrentTransferred(0L);

						final String clientId = plugin.getClientPluginAccess().getClientID();
						UUID uuid = null;
						RemoteFileData remoteFile = null;
						InputStream is = null;
						Exception ex = null;
						try
						{
							is = new FileInputStream(file);
							uuid = service.openTransfer(clientId, serverFileName);
							if (uuid != null)
							{
								byte[] buffer = new byte[CHUNK_BUFFER_SIZE];
								int read = is.read(buffer);
								while (read > -1 && !progressMonitor.js_isCanceled())
								{
									service.writeBytes(uuid, buffer, 0, read);
									totalTransfered += read;
									currentTransferred += read;
									progressMonitor.setTotalTransferred(totalTransfered);
									progressMonitor.setCurrentTransferred(currentTransferred);

									if (progressMonitor.getDelay() > 0)
									{
										Thread.sleep(progressMonitor.getDelay()); // to test the process
									}

									read = is.read(buffer);
								}
							}
						}
						catch (final Exception e)
						{
							Debug.error(e);
							ex = e;
						}
						finally
						{
							try
							{
								if (uuid != null)
								{
									remoteFile = (RemoteFileData)service.closeTransfer(uuid);
								}
							}
							catch (final RemoteException ignore)
							{
							}
							try
							{
								if (is != null) is.close();
							}
							catch (final IOException ignore)
							{
							}
							if (function != null && !progressMonitor.js_isCanceled())
							{
								final JSFile returnedFile = (remoteFile == null) ? null : new JSFile(new RemoteFile(remoteFile, service, clientId));
								function.executeAsync(plugin.getClientPluginAccess(), new Object[] { returnedFile, ex });
							}
							if (progressMonitor.js_isCanceled())
							{
								try
								{
									service.delete(clientId, remoteFile.getAbsolutePath());
									progressMonitor.run();
									break;
								}
								catch (final IOException ignore)
								{
								}
							}
						}
					}
				}
			}
			finally
			{
				if (!progressMonitor.js_isCanceled())
				{
					progressMonitor.setFinished(true);
					progressMonitor.run();
				}
				progressMonitor.cancel(); // stops the TimerTask
			}
		}
	}

	/**
	 * helper function to get int value of java.lang.Numeber object or default value in case of null
	 * @param numberObject
	 * @param defaultValue
	 * @return
	 */
	private int getNumberAsInt(Number numberObject, int defaultValue)
	{
		return numberObject == null ? defaultValue : numberObject.intValue();
	}

	/**
	 * helper function to get boolean value of java.lang.Boolean object or default value in case of null
	 * @param booleanObject
	 * @param defaultValue
	 * @return
	 */
	private boolean getBooleanAsbool(Boolean booleanObject, boolean defaultValue)
	{
		return booleanObject == null ? defaultValue : booleanObject.booleanValue();
	}
}
