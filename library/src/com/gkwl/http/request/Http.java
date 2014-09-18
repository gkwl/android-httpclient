package com.gkwl.http.request;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;

import org.apache.http.util.ByteArrayBuffer;

import android.text.TextUtils;

import com.gkwl.http.Connection;

public abstract class Http {
	protected static final String CL = "\r\n";
	protected static final String DOUBLE_CL = "\r\n\r\n";
	
	private Connection conn;
	private int readBufSize = 8192;
	private boolean keepConnection = false;
	
	private URL reqUrl;
	protected byte[] reqBody;
	protected HashMap<String, String> reqHeaders = new HashMap<String, String>();
	protected HashMap<String, String> reqParams = new HashMap<String, String>();
	
	private int resCode;
	private byte[] resRaw;
	private byte[] resBody;
	private HashMap<String, List<String>> resHeaders = new HashMap<String, List<String>>();
	
	protected abstract String method();
	
	protected void appendHeaders(OutputStream os) throws IOException {
	}
	
	protected String getResource() throws NullPointerException {
		return reqUrl.getPath().equals("") ? "/" : reqUrl.getPath();
	}
	
	public Http() {
	}
	
	public Http(Connection conn) {
		this.conn = conn;
	}
	
	public void close() throws NullPointerException, IOException {
		conn.close();
	}
	
	public void setUrl(String urlStr) throws MalformedURLException, IllegalArgumentException {
		URL url = new URL(urlStr);
		if (!url.getProtocol().equals("http"))
			throw new IllegalArgumentException();
		this.reqUrl = url;
	}
	
	public URL getUrl() {
		return reqUrl;
	}
	
	public void setConnection(Connection conn) {
		this.conn = conn;
	}
	
	public void setKeepConnection(boolean keep) {
		keepConnection = keep;
	}
	
	public void setBody(byte[] body) {
		this.reqBody = body;
	}
	
	public byte[] getResBody() {
		return resBody;
	}
	
	public byte[] getResRaw() {
		return resRaw;
	}
	
	public int getResCode() {
		return resCode;
	}
	
	public Map<String, List<String>> getHeaders() {
		return resHeaders;
	}
	
	protected boolean isChunkedEnd(byte[] buf, int read) throws NullPointerException, IllegalArgumentException {
		if (read < 5 || buf.length < read)
			throw new IllegalArgumentException();
		return buf[read - 1] == '\n' && buf[read - 2] == '\r' && buf[read - 3] == '\n' && buf[read - 4] == '\r' && buf[read - 5] == '0';
	}
	
	protected byte[] readResponse(InputStream is) throws NullPointerException, IOException, IllegalArgumentException, NumberFormatException {
		ByteArrayBuffer bab = new ByteArrayBuffer(readBufSize);
	    byte[] buf = new byte[readBufSize];
	    int read = -1;

	    boolean chunked = false;
	    int contentLength = 0;
	    boolean noneChunkedOrLength = false;
	    
	    int readContentLength = 0;
	    boolean foundEmptyLine = false;
	    
		while (true) {
			read = is.read(buf);
			
			if (read > -1)
				bab.append(buf, 0, read);
			else if (!noneChunkedOrLength)
				throw new IOException();
			
			if (!foundEmptyLine) {
				byte[] partByte = bab.toByteArray();
				String partBody = new String(partByte).toLowerCase();
				
				if (partBody.contains("connection: close"))
					keepConnection = false;
				
				int emptyLineIndex = partBody.indexOf(DOUBLE_CL);
				if (emptyLineIndex != -1) {
					foundEmptyLine = true;
					if (partBody.contains("chunked")) {
						chunked = true;
					} else if (partBody.contains("content-length")) {
						int b = partBody.indexOf("content-length");
						if (b > emptyLineIndex)
							throw new IllegalArgumentException();
						int e = partBody.indexOf(CL, b);
						
						String contentLengthHeader = partBody.substring(b, e);
						String[] parts = contentLengthHeader.split(": ");
						if (parts.length != 2)
							throw new IllegalArgumentException();
						contentLength = Integer.parseInt(parts[1]);
						read = partByte.length - emptyLineIndex - DOUBLE_CL.length();
					} else {
						noneChunkedOrLength = true;
					}
				}
			}
			
			if (chunked) {
				if (isChunkedEnd(buf, read))
					break;
			} else if (contentLength != 0) {
				readContentLength += read;
				if (readContentLength == contentLength)
					break;
			} else if (noneChunkedOrLength) {
				if (read == -1)
					break;
			}
		}
		
		resRaw = bab.toByteArray();
		resCode = parseCode(resRaw);
		resHeaders.clear();
		resHeaders.putAll(parseHeaders(resRaw));
		resBody = parseBody(resRaw);
		
		return resBody;
	}
	
	protected HashMap<String, List<String>> parseHeaders(byte[] rawByte) throws NullPointerException, IllegalArgumentException {
		String raw = new String(rawByte);
		int statusCl = raw.indexOf(CL);
		if (statusCl == -1)
			throw new IllegalArgumentException();
		int emptyLine = raw.indexOf(DOUBLE_CL, statusCl + 1);
		if (emptyLine == -1)
			throw new IllegalArgumentException();
		String headers = raw.substring(statusCl + CL.length(), emptyLine);
		
		HashMap<String, List<String>> ms = new HashMap<String, List<String>>();
		String[] hs = headers.split(CL);
		for (String s : hs) {
			int t = s.indexOf(": ");
			if (t != -1) {
				String key = s.substring(0, t);
				String value = s.substring(t + ": ".length());
				if (TextUtils.isEmpty(key))
					continue;
				
				if (!ms.containsKey(key))
					ms.put(key, new LinkedList<String>());
				ms.get(key).add(value);
			}
		}
		
		return ms;
	}
	
	protected byte[] decodeChunked(byte[] body, int bodyBegin) throws NullPointerException, IllegalArgumentException, NumberFormatException {
		ByteArrayBuffer bab = new ByteArrayBuffer(body.length);
		final int crlfByteCount = 2;
		int byteCountEnd = bodyBegin;
		int byteCountBegin = bodyBegin;
		
		while (true) {
			if (byteCountEnd > body.length - 2)
				throw new IllegalArgumentException();
			
			int f = body[byteCountEnd];
			int s = body[byteCountEnd + 1];
			
			if (f != '\r' || s != '\n') {
				byteCountEnd++;
			} else {
				String hex = new String(body, byteCountBegin, byteCountEnd - byteCountBegin);
				if (hex.equals("0"))
					break;
				Integer decimal = Integer.parseInt(hex, 16);
				
				int chunkedBegin = byteCountEnd + crlfByteCount;
				if (chunkedBegin >= body.length || chunkedBegin + decimal > body.length)
					throw new IllegalArgumentException();
				
				bab.append(body, chunkedBegin, decimal);
				
				byteCountBegin = chunkedBegin + decimal + crlfByteCount;
				byteCountEnd = byteCountBegin;
			}
		}
		return bab.toByteArray();
	}
	
	protected byte[] decodeGzipped(byte[] gzippedBody) throws NullPointerException, IOException {
		ByteArrayBuffer bab = new ByteArrayBuffer(gzippedBody.length);
		ByteArrayInputStream bais = new ByteArrayInputStream(gzippedBody);
		GZIPInputStream zis = new GZIPInputStream(bais);
		int read = -1;
		byte[] buf = new byte[readBufSize];
		while ((read = zis.read(buf)) != -1)
			bab.append(buf, 0, read);
		return bab.toByteArray();
	}
	
	protected byte[] parseBody(byte[] rawByte) throws NullPointerException, IOException, IllegalArgumentException {
		String raw = new String(rawByte);
		int first = raw.indexOf(DOUBLE_CL);
		if (first == -1)
			throw new IllegalArgumentException();
		
		String upper = raw.substring(0, first);
		
		boolean chunked = upper.toLowerCase().contains("transfer-encoding: chunked");
		boolean gzipped = upper.toLowerCase().contains("content-encoding: gzip");
		
		byte[] body = null;
		int bodyBegin = upper.getBytes().length + DOUBLE_CL.getBytes().length;
		
		if (chunked)
			body = decodeChunked(rawByte, bodyBegin);
		else
			body = Arrays.copyOfRange(rawByte, bodyBegin, rawByte.length);
		
		if (gzipped)
			body = decodeGzipped(body);
		
		return body;
	}
	
	protected int parseCode(byte[] rawByte) throws NullPointerException, IllegalArgumentException, NumberFormatException {
		String raw = new String(rawByte);
		int first = raw.indexOf(CL);
		if (first == -1)
			throw new IllegalArgumentException();
		String statusLine = raw.substring(0, first);
		String[] parts = statusLine.split(" ");
		if (parts.length < 2)
			throw new IllegalArgumentException();
		return Integer.parseInt(parts[1]);
	}
	
	protected String makeParams(StringBuffer sb) throws NullPointerException {
		Iterator<Entry<String, String>> itor = reqParams.entrySet().iterator();
		while (itor.hasNext()) {
			Entry<String, String> en = itor.next();
			if (TextUtils.isEmpty(en.getKey()))
				continue;
			
			String value = en.getValue();
			if (value == null)
				value = "";
			
			sb.append(URLEncoder.encode(en.getKey()))
			  .append("=")
			  .append(URLEncoder.encode(value));
			if (itor.hasNext())
				sb.append("&");
		}
		
		return sb.toString();
	}
	
	public byte[] execute() throws NullPointerException, IOException, IllegalArgumentException, NumberFormatException {
		if (conn != null && !conn.isAvaliable())
			throw new IllegalArgumentException();
		
		String host = (conn == null ? reqUrl.getHost() : conn.getHost());
		String res = getResource();
		
		if (conn == null)
			conn = new Connection(host, reqUrl.getPort() == -1 ? 80 : reqUrl.getPort());
		
		// headers
		BufferedOutputStream os = new BufferedOutputStream(conn.getOutPutstream());
		os.write((method() + " " + res + " HTTP/1.1" + CL).getBytes());
		os.write(("Host: " + host + CL).getBytes());
		Iterator<Entry<String, String>> itor = reqHeaders.entrySet().iterator();
		while (itor.hasNext()) {
			Entry<String, String> en = itor.next();
			os.write((en.getKey() + ": " + en.getValue() + CL).getBytes());
		}
		if (keepConnection && !reqHeaders.containsKey("Connection"))
			os.write(("Connection: Keep-Alive" + CL).getBytes());
		appendHeaders(os);
		
		os.write(CL.getBytes());
		
		// body
		if (reqBody != null)
			os.write(reqBody);
		
		os.flush();
		
		// response
		byte[] response = readResponse(conn.getInputStream());
	    
		// if should close
	    if (!keepConnection)
	    	conn.close();
	    
		return response;
	}
	
	public void addParams(Map<String, String> params) throws NullPointerException {
		this.reqParams.putAll(params);
	}
	
	public void addParam(String key, String value) throws NullPointerException {
		reqParams.put(key, value);
	}
	
	public void removeParam(String key) {
		reqParams.remove(key);
	}
	
	public void clearParam() {
		reqParams.clear();
	}
	
	public void addHeaders(Map<String, String> headers) throws NullPointerException {
		this.reqHeaders.putAll(headers);
	}
	
	public void addHeader(String key, String value) throws NullPointerException {
		reqHeaders.put(key, value);
	}
	
	public void removeHeader(String key) {
		reqHeaders.remove(key);
	}
	
	public void clearHeader() {
		reqHeaders.clear();
	}
}
