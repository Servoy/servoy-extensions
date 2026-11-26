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
package com.servoy.extensions.plugins.pdf_forms.servlets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.adobe.fdf.FDFDoc;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.servoy.j2db.dataprocessing.BufferedDataSet;
import com.servoy.j2db.dataprocessing.IDataServer;
import com.servoy.j2db.dataprocessing.ISQLActionTypes;
import com.servoy.j2db.dataprocessing.ISQLStatement;
import com.servoy.j2db.persistence.IServer;
import com.servoy.j2db.persistence.ITable;
import com.servoy.j2db.plugins.IServerAccess;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The pdf_forms servlet with mocked JDBC (GET: the pdf form as FDF/XDP and the template) and a mocked data server
 * (POST: storing the submitted values). The FDF data is created and read with the Adobe FDF toolkit. No database.
 */
public class PDFServletTest
{
	private final Properties settings = new Properties();
	private IServerAccess access;
	private Connection connection;
	private final Map<String, Object[][]> queries = new HashMap<>();
	private IDataServer dataServer;
	private HttpServletRequest request;
	private HttpServletResponse response;
	private final ByteArrayOutputStream body = new ByteArrayOutputStream();
	private final StringWriter writer = new StringWriter();

	@BeforeEach
	public void setUp() throws Exception
	{
		access = mock(IServerAccess.class);
		when(access.getSettings()).thenReturn(settings);
		connection = mock(Connection.class);
		when(connection.createStatement()).thenAnswer(inv -> statement());
		when(access.getDBServerConnection("pdf_forms", null)).thenReturn(connection);

		ApplicationServerRegistry.destroy();
		IApplicationServerSingleton server = mock(IApplicationServerSingleton.class);
		dataServer = mock(IDataServer.class);
		when(server.getDataServer()).thenReturn(dataServer);
		when(server.getClientId()).thenReturn("server-client");
		ApplicationServerRegistry.setApplicationServerSingleton(server);

		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);
		when(request.getScheme()).thenReturn("https");
		when(request.getServerName()).thenReturn("app.example");
		when(request.getServerPort()).thenReturn(Integer.valueOf(8443));
		when(response.getOutputStream()).thenReturn(new ServletOutputStream()
		{
			@Override
			public void write(int b)
			{
				body.write(b);
			}

			@Override
			public boolean isReady()
			{
				return true;
			}

			@Override
			public void setWriteListener(WriteListener writeListener)
			{
			}
		});
		when(response.getWriter()).thenReturn(new PrintWriter(writer));
	}

	@AfterEach
	public void tearDown()
	{
		ApplicationServerRegistry.destroy();
	}

	/** A statement that answers the registered query prefixes with rows. */
	private Statement statement() throws Exception
	{
		Statement statement = mock(Statement.class);
		when(statement.executeQuery(anyString())).thenAnswer(inv -> resultSet(inv.getArgument(0)));
		return statement;
	}

	private ResultSet resultSet(String sql) throws Exception
	{
		Object[][] rows = queries.entrySet().stream().filter(e -> sql.startsWith(e.getKey())).map(Map.Entry::getValue).findFirst()
			.orElse(new Object[0][]);
		ResultSet rs = mock(ResultSet.class);
		int[] index = { -1 };
		when(Boolean.valueOf(rs.next())).thenAnswer(inv -> Boolean.valueOf(++index[0] < rows.length));
		when(Integer.valueOf(rs.getInt(anyInt()))).thenAnswer(inv -> Integer.valueOf(((Number)rows[index[0]][(int)inv.getArgument(0) - 1]).intValue()));
		when(rs.getString(anyInt())).thenAnswer(inv -> (String)rows[index[0]][(int)inv.getArgument(0) - 1]);
		when(rs.getBytes(anyInt())).thenAnswer(inv -> (byte[])rows[index[0]][(int)inv.getArgument(0) - 1]);
		when(Boolean.valueOf(rs.getBoolean(anyInt()))).thenAnswer(inv -> rows[index[0]][(int)inv.getArgument(0) - 1]);
		return rs;
	}

	private static byte[] pdf()
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4);
		PdfWriter.getInstance(document, out);
		document.open();
		document.add(new Paragraph("form"));
		document.close();
		return out.toByteArray();
	}

	/** The FDF as text, with the (Flate compressed) JavaScript streams inflated. */
	private static String fdfText(byte[] fdf) throws Exception
	{
		String raw = new String(fdf, StandardCharsets.ISO_8859_1);
		StringBuilder text = new StringBuilder(raw);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("/Length (\\d+)[^>]*>>\\s*stream\\r?\\n").matcher(raw);
		while (m.find())
		{
			int start = m.end();
			// raw deflate without the zlib header and checksum (the FDF toolkit's adler32 does not always check out)
			java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
			inflater.setInput(fdf, start + 2, Integer.parseInt(m.group(1)) - 2);
			byte[] buffer = new byte[64 * 1024];
			int length = inflater.inflate(buffer);
			inflater.end();
			text.append(new String(buffer, 0, length, StandardCharsets.ISO_8859_1));
		}
		return text.toString();
	}

	private PDFServlet servlet()
	{
		return new PDFServlet(access);
	}

	private void get(String path) throws Exception
	{
		when(request.getPathInfo()).thenReturn(path);
		// the path info already starts with the servlet name (/pdf_forms/...)
		when(request.getRequestURI()).thenReturn(path == null ? "/servoy-service/pdf_forms" : "/servoy-service" + path);
		servlet().doGet(request, response);
	}

	/** action 7 for form 3 and template 5; editable (1) or view only (0); open (0) or closed (1). */
	private void action(int actionType, int closed)
	{
		queries.put("select form_id,template_id,action_type,closed from pdf_actions",
			new Object[][] { { Integer.valueOf(3), Integer.valueOf(5), Integer.valueOf(actionType), Integer.valueOf(closed) } });
		queries.put("select actual_pdf_form from pdf_templates", new Object[][] { { pdf() } });
		queries.put("select value_name,field_value from pdf_form_values",
			new Object[][] { { "name", "Servoy" }, { "city", "Amersfoort" }, { "empty", null } });
		queries.put("select filename,skip_placing_submit_button from pdf_templates", new Object[][] { { "order.pdf", Boolean.FALSE } });
	}

	// --- GET: routing ---

	@Test
	public void unknownPathsAreNotFound() throws Exception
	{
		get("/");
		get("/other");

		verify(response, times(2)).sendError(404);
	}

	@Test
	public void withoutPathTheRequestIsRedirectedToTheFolder() throws Exception
	{
		get(null);

		verify(response).sendRedirect("/servoy-service/pdf_forms/");
	}

	// --- GET: pdf form ---

	@Test
	public void anEditableFormIsAnFdfWithTheValuesAndASubmitButton() throws Exception
	{
		action(1, 0);
		when(request.getParameter("action_id")).thenReturn("7");

		get("/pdf_forms/pdf_form");

		verify(response).setContentType("application/vnd.fdf");
		verify(response).setHeader("Cache-Control", "max-age=60, must-revalidate, proxy-revalidate");
		FDFDoc fdf = new FDFDoc(body.toByteArray());
		assertEquals("Servoy", fdf.GetValue("name"));
		assertEquals("Amersfoort", fdf.GetValue("city"));
		assertEquals("7", fdf.GetValue("servoy_action_id"));
		assertEquals("https://app.example:8443/servoy-service/pdf_forms/pdf_process_data", fdf.GetValue("servoy_pdf_submit_url"));
		assertTrue(fdf.GetFile().startsWith("https://app.example:8443/servoy-service/pdf_forms/pdf_template/order.pdf?template_id=5&rnd="),
			fdf.GetFile());
		String raw = fdfText(body.toByteArray());
		assertTrue(raw.contains("servoySubmit"), raw);
		assertTrue(raw.contains("pdf_process_data?action_id=7"), raw);
		assertFalse(java.util.Arrays.asList(fdf.GetFieldNames()).contains("empty"));
		verify(connection).close();
	}

	@Test
	public void aViewOnlyFormMakesTheFieldsReadOnly() throws Exception
	{
		action(0, 0);
		when(request.getParameter("action_id")).thenReturn("7");
		when(request.getServerPort()).thenReturn(Integer.valueOf(443));
		settings.setProperty(PDFServlet.TEMPLATE_LOCATION_PROPERTY, "https://cdn.example/templates");

		get("/pdf_forms/pdf_form");

		FDFDoc fdf = new FDFDoc(body.toByteArray());
		// the default port is left out, the generic template location gets a slash
		assertEquals("https://cdn.example/templates/order.pdf", fdf.GetFile());
		String raw = fdfText(body.toByteArray());
		assertTrue(raw.contains("readonly = true"), raw);
		assertFalse(raw.contains("servoySubmit"), raw);
		// a plain (not XFA) form always gets the action id and submit url
		assertEquals("7", fdf.GetValue("servoy_action_id"));
	}

	@Test
	public void theTemplateLocationCanBeOverridden() throws Exception
	{
		action(1, 0);
		queries.put("select filename,skip_placing_submit_button from pdf_templates", new Object[][] { { "order.pdf", Boolean.TRUE } });
		when(request.getParameter("action_id")).thenReturn("7");
		when(request.getParameter("overrideTemplateLocation")).thenReturn("https://other.example/x.pdf");

		get("/pdf_forms/pdf_form");

		FDFDoc fdf = new FDFDoc(body.toByteArray());
		assertEquals("https://other.example/x.pdf", fdf.GetFile());
		// the submit button is skipped for this template
		assertFalse(fdfText(body.toByteArray()).contains("servoySubmit"));
	}

	@Test
	public void aClosedActionIsRefused() throws Exception
	{
		action(1, 1);
		when(request.getParameter("action_id")).thenReturn("7");

		get("/pdf_forms/pdf_form");

		verify(response).setContentType("text/html");
		assertTrue(writer.toString().contains("Security violation"), writer.toString());
	}

	@Test
	public void anUnknownOrMissingActionGivesNothing() throws Exception
	{
		when(request.getParameter("action_id")).thenReturn("7");
		get("/pdf_forms/pdf_form");
		when(request.getParameter("action_id")).thenReturn(null);
		get("/pdf_forms/pdf_form");

		assertEquals(0, body.size());
		verify(response, never()).setContentType(anyString());
	}

	@Test
	public void withoutDatabaseServerNothingIsServed() throws Exception
	{
		settings.setProperty(PDFServlet.SERVER_NAME_PROPERTY, "missing_server");
		when(request.getParameter("action_id")).thenReturn("7");
		when(request.getParameter("template_id")).thenReturn("5");

		get("/pdf_forms/pdf_form");
		get("/pdf_forms/pdf_template/order.pdf");

		assertEquals(0, body.size());
		verify(access, times(2)).getDBServerConnection("missing_server", null);
	}

	@Test
	public void theActionIdIsANumberInTheQuery() throws Exception
	{
		when(request.getParameter("action_id")).thenReturn("7 or 1=1");
		Statement statement = statement();
		when(connection.createStatement()).thenReturn(statement);

		get("/pdf_forms/pdf_form");

		// the id is parsed as a number, not concatenated as text
		verify(statement, never()).executeQuery(anyString());
	}

	// --- GET: template ---

	@Test
	public void theTemplateIsServedAsPdf() throws Exception
	{
		byte[] pdf = pdf();
		queries.put("select actual_pdf_form from pdf_templates", new Object[][] { { pdf } });
		when(request.getParameter("template_id")).thenReturn("5");

		get("/pdf_forms/pdf_template/order.pdf");

		verify(response).setContentType("application/pdf");
		verify(response).setContentLength(pdf.length);
		assertArrayEquals(pdf, body.toByteArray());
	}

	@Test
	public void aTemplateWithoutContentIsNotFound() throws Exception
	{
		queries.put("select actual_pdf_form from pdf_templates", new Object[][] { { null } });
		when(request.getParameter("template_id")).thenReturn("5");

		get("/pdf_forms/pdf_template/order.pdf");

		verify(response).sendError(404);
	}

	@Test
	public void aTemplateWithoutIdIsNotFound() throws Exception
	{
		get("/pdf_forms/pdf_template/order.pdf");

		verify(response).sendError(404);
	}

	// --- POST: storing submitted values ---

	private void post(String path, byte[] content) throws Exception
	{
		when(request.getPathInfo()).thenReturn(path);
		// the path info already starts with the servlet name (/pdf_forms/...)
		when(request.getRequestURI()).thenReturn(path == null ? "/servoy-service/pdf_forms" : "/servoy-service" + path);
		ByteArrayInputStream in = new ByteArrayInputStream(content);
		when(request.getInputStream()).thenReturn(new ServletInputStream()
		{
			@Override
			public int read()
			{
				return in.read();
			}

			@Override
			public boolean isFinished()
			{
				return in.available() == 0;
			}

			@Override
			public boolean isReady()
			{
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener)
			{
			}
		});
		servlet().doPost(request, response);
	}

	private static byte[] submitted(String... nameValues) throws Exception
	{
		FDFDoc fdf = new FDFDoc();
		for (int i = 0; i < nameValues.length; i += 2)
		{
			fdf.SetValue(nameValues[i], nameValues[i + 1]);
		}
		return fdf.Save();
	}

	/** action 7: form 3, template 5, open, without redirect; form 3 has the value "name" with fval_id 11 and "old" with 12. */
	private void openAction(String redirect) throws Exception
	{
		when(dataServer.performQuery(eq("server-client"), eq("pdf_forms"), eq("pdf_actions"), isNull(), anyString(), any(), eq(0), eq(-1)))
			.thenReturn(new BufferedDataSet(new String[] { "form_id", "template_id", "closed", "redirect_url" },
				List.<Object[]> of(new Object[] { Integer.valueOf(3), Integer.valueOf(5), Integer.valueOf(0), redirect })));
		when(dataServer.performQuery(eq("server-client"), eq("pdf_forms"), eq("pdf_templates"), isNull(), anyString(), any(), eq(0), eq(-1)))
			.thenReturn(new BufferedDataSet(new String[] { "redirect_url" }, List.<Object[]> of(new Object[] { null })));
		when(dataServer.performQuery(eq("server-client"), eq("pdf_forms"), eq("pdf_form_values"), isNull(), anyString(), any(), eq(0), eq(-1)))
			.thenReturn(new BufferedDataSet(new String[] { "value_name", "fval_id" },
				List.<Object[]> of(new Object[] { "name", Integer.valueOf(11) }, new Object[] { "old", Integer.valueOf(12) })));
		when(dataServer.createSQLStatement(anyInt(), anyString(), anyString(), any(), any(), anyString(), any()))
			.thenAnswer(inv -> statementFor(inv.getArgument(0), inv.getArgument(2), inv.getArgument(5), inv.getArgument(6)));
	}

	private final List<String[]> statements = new ArrayList<>();

	private ISQLStatement statementFor(int action, String table, String sql, Object[] args)
	{
		statements.add(new String[] { String.valueOf(action), table, sql, java.util.Arrays.toString(args) });
		return mock(ISQLStatement.class);
	}

	@Test
	public void submittedValuesAreUpdatedInsertedAndLeftoversDeleted() throws Exception
	{
		openAction(null);
		when(access.getNextSequence("pdf_forms", "pdf_form_values", "fval_id")).thenReturn(Integer.valueOf(20));
		IServer dbServer = mock(IServer.class);
		ITable table = mock(ITable.class);
		when(access.getDBServer("pdf_forms", true, true)).thenReturn(dbServer);
		when(dbServer.getTable("pdf_form_values")).thenReturn(table);
		when(table.getColumnNames()).thenReturn(new String[] { "fval_id", "form_id", "value_name", "field_value", "other" });

		post("/pdf_forms/pdf_process_data", submitted("servoy_action_id", "7", "name", "New name", "city", "Utrecht"));

		assertTrue(statements.stream().anyMatch(s -> s[0].equals(String.valueOf(ISQLActionTypes.UPDATE_ACTION)) &&
			s[2].equals("update pdf_form_values set field_value = ? where fval_id = ?") && s[3].equals("[New name, 11]")), statements.toString());
		assertTrue(statements.stream().anyMatch(s -> s[2].equals("insert into pdf_form_values (fval_id,form_id,value_name,field_value) values (?,?,?,?)") &&
			s[3].equals("[20, 3, city, Utrecht]")), statements.toString());
		assertTrue(statements.stream().anyMatch(s -> s[2].equals("delete from pdf_form_values where fval_id = ?") && s[3].equals("[12]")),
			statements.toString());
		assertTrue(statements.stream().anyMatch(s -> s[2].equals("update pdf_actions set closed = 1 where action_id = ?")), statements.toString());
		// the action id is not stored as a value
		assertTrue(statements.stream().noneMatch(s -> s[3].contains("servoy_action_id")));
		assertTrue(writer.toString().contains("Data successfully stored"), writer.toString());
		verify(dataServer, times(4)).performUpdates(eq("server-client"), any());
	}

	@Test
	public void withADatabaseIdentityTheIdIsLeftOut() throws Exception
	{
		openAction(null);

		post("/pdf_forms/pdf_process_data", submitted("servoy_action_id", "7", "city", "Utrecht"));

		// no table information: the default column order
		assertTrue(statements.stream().anyMatch(s -> s[2].equals("insert into pdf_form_values (value_name,field_value,form_id) values (?,?,?)") &&
			s[3].equals("[city, Utrecht, 3]")), statements.toString());
	}

	@Test
	public void theActionIdCanBeAParameterAndARedirectIsFollowed() throws Exception
	{
		openAction("https://app.example/thanks");
		when(request.getParameter("action_id")).thenReturn("7");

		post("/pdf_forms/pdf_process_data", submitted("name", "x"));

		// the redirect url is used as it is, not url-encoded
		verify(response).sendRedirect("https://app.example/thanks");
		assertEquals("", writer.toString());
	}

	@Test
	public void anXfaSubmissionIsReadAsXml() throws Exception
	{
		openAction(null);
		when(request.getParameter("action_id")).thenReturn("7");
		String xml = "<?xml version='1.0'?><xdp><form><name>Xml name</name><city>Zwolle</city></form></xdp>";

		post("/pdf_forms/pdf_process_data", xml.getBytes(StandardCharsets.UTF_8));

		assertTrue(statements.stream().anyMatch(s -> s[3].equals("[Xml name, 11]")), statements.toString());
		assertTrue(statements.stream().anyMatch(s -> s[3].contains("Zwolle")), statements.toString());
	}

	@Test
	public void aClosedActionIsNotUpdated() throws Exception
	{
		when(dataServer.performQuery(anyString(), anyString(), eq("pdf_actions"), isNull(), anyString(), any(), eq(0), eq(-1)))
			.thenReturn(new BufferedDataSet(new String[] { "form_id", "template_id", "closed", "redirect_url" },
				List.<Object[]> of(new Object[] { Integer.valueOf(3), Integer.valueOf(5), Integer.valueOf(1), "https://app.example/x" })));

		post("/pdf_forms/pdf_process_data", submitted("servoy_action_id", "7", "name", "x"));

		assertTrue(writer.toString().contains("Security violation"), writer.toString());
		// the violation is the only answer: no redirect and no success message after it
		assertFalse(writer.toString().contains("successfully"), writer.toString());
		verify(response, never()).sendRedirect(anyString());
		verify(dataServer, never()).performUpdates(anyString(), any());
	}

	@Test
	public void anUnknownActionIsNotFound() throws Exception
	{
		when(dataServer.performQuery(anyString(), anyString(), eq("pdf_actions"), isNull(), anyString(), any(), eq(0), eq(-1)))
			.thenReturn(new BufferedDataSet(new String[] { "form_id", "template_id", "closed", "redirect_url" }, List.<Object[]> of()));

		post("/pdf_forms/pdf_process_data", submitted("servoy_action_id", "8", "name", "x"));

		assertTrue(writer.toString().contains("action_id not found"), writer.toString());
		assertFalse(writer.toString().contains("successfully"), writer.toString());
		verify(dataServer, never()).performUpdates(anyString(), any());
	}

	@Test
	public void withoutActionIdNothingIsStored() throws Exception
	{
		post("/pdf_forms/pdf_process_data", submitted("name", "x"));

		assertTrue(writer.toString().contains("action_id not found"), writer.toString());
		verify(dataServer, never()).performUpdates(anyString(), any());
	}

	@Test
	public void anXmlSubmissionWithoutActionIdParameterFails() throws Exception
	{
		// an xml (XFA) submission has no FDF to read the action id from
		post("/pdf_forms/pdf_process_data", "<form><name>x</name></form>".getBytes(StandardCharsets.UTF_8));

		assertTrue(writer.toString().contains("action_id not found"), writer.toString());
		verify(dataServer, never()).performUpdates(anyString(), any());
	}

	@Test
	public void postRouting() throws Exception
	{
		post(null, new byte[0]);
		post("/", new byte[0]);
		post("/other", new byte[0]);

		verify(response).sendRedirect("/servoy-service/pdf_forms/");
		verify(response, times(2)).sendError(404);
	}

	@Test
	public void theTemplateFallsBackToTheRedirectOfTheTemplate() throws Exception
	{
		openAction(" ");
		when(dataServer.performQuery(eq("server-client"), eq("pdf_forms"), eq("pdf_templates"), isNull(), anyString(), any(), eq(0), eq(-1)))
			.thenReturn(new BufferedDataSet(new String[] { "redirect_url" }, List.<Object[]> of(new Object[] { "https://app.example/template" })));
		ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);

		post("/pdf_forms/pdf_process_data", submitted("servoy_action_id", "7"));

		verify(response).sendRedirect(redirect.capture());
		assertEquals("https://app.example/template", redirect.getValue());
	}
}
