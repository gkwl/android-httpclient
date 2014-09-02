package com.gkwl.http.request;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import com.gkwl.http.util.UrlUtil;

public abstract class Http {
	protected static final String CL = "\r\n";
	
	private Connection conn;
	private String url;
	protected byte[] postBody;
	
	private int resCode;
	private byte[] resRaw;
	private byte[] resBody;
	private Map<String, List<String>> resHeaders;
	
	private int readBufSize = 8192;
	private boolean keepConnection = false;
	
	protected Map<String, String> headers = new HashMap<String, String>();
	protected Map<String, String> params = new HashMap<String, String>();
	
	protected abstract String method();
	
	protected void appendHeaders(OutputStream os) {
	}
	
	protected String getResource(String url) {
		return UrlUtil.getResource(url);
	}
	
	public Http() {
	}
	
	public Http(Connection conn) {
		this.conn = conn;
	}
	
	public void close() throws IOException {
		conn.close();
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getUrl() {
		return url;
	}
	
	public void setConnection(Connection conn) {
		this.conn = conn;
	}
	
	public void setKeepConnection(boolean keep) {
		keepConnection = keep;
	}
	
	public void setBody(byte[] body) {
		this.postBody = body;
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
	
	private boolean isChunkedEnd(byte[] buf) {
		return buf[buf.length -1 ] == 10 && buf[buf.length - 2] == 13 && buf[buf.length - 3] == 10 && buf[buf.length - 4] == 13 && buf[buf.length - 5] == 48;
	}
	
	private byte[] readResponse(Connection conn) throws IOException {
		ByteArrayBuffer bab = new ByteArrayBuffer(readBufSize);
		InputStream is = conn.getInputStream();
	    byte[] buf = new byte[readBufSize];
	    int read = -1;

	    boolean chunked = false;
	    int contentLength = 0;
	    int readContentLength = 0;
	    boolean foundEmptyLine = false;
	    
		while (true) {
			read = is.read(buf);
			if (read == -1)
				throw new IOException("Unexpected end of the stream");
			
			bab.append(buf, 0, read);
			if (foundEmptyLine) {
				if (chunked) {
					if (isChunkedEnd(buf))
						break;
				} else {
					readContentLength += read;
					if (readContentLength == contentLength)
						break;
				}
			} else {
				byte[] partByte = bab.toByteArray();
				String partBody = new String(partByte, 0, partByte.length);
				int emptyLineIndex = partBody.indexOf(CL + CL);
				if (emptyLineIndex != -1) {
					foundEmptyLine = true;
					if (partBody.toLowerCase().contains("chunked")) {
						chunked = true;
						
						if (isChunkedEnd(buf))
							break;
					}
					
					int in = partBody.toLowerCase().indexOf("content-length");
					if (in != -1) {
						int j = partBody.indexOf(CL, in);
						String h = partBody.substring(in, j);
						String[] parts = h.split(": ");
						contentLength = Integer.parseInt(parts[1]);
						
						readContentLength += partByte.length - partBody.substring(0, emptyLineIndex + (CL + CL).length()).getBytes().length;
						if (readContentLength == contentLength)
							break;
					}
				}
			}
		}
		
		resRaw = bab.toByteArray();
		resCode = parseCode(resRaw);
		resHeaders = parseHeaders(resRaw);
		resBody = parseBody(resRaw);
		
		return resBody;
	}
	
	private Map<String, List<String>> parseHeaders(byte[] rawByte) {
		String raw = new String(rawByte);
		int statusCl = raw.indexOf(CL);
		int emptyLine = raw.indexOf(CL + CL);
		String headers = raw.substring(statusCl + CL.length(), emptyLine);
		
		Map<String, List<String>> ms = new HashMap<String, List<String>>();
		String[] hs = headers.split(CL);
		for (String s : hs) {
			String[] parts = s.split(": ");
			if (!ms.containsKey(parts[0]))
				ms.put(parts[0], new LinkedList<String>());
			ms.get(parts[0]).add(parts[1]);
		}
		
		return ms;
	}
	
	private byte[] decodeChunked(byte[] body) {
		ByteArrayBuffer bab = new ByteArrayBuffer(body.length);
		final int crlfByteCount = 2;
		int byteCountEnd = 0;
		int byteCountBegin = 0;
		
		while (true) {
			int f = body[byteCountEnd];
			int s = body[byteCountEnd + 1];
			
			// \r:13 \n:10
			if (f != 13 || s != 10) {
				byteCountEnd++;
			} else {
				String hex = new String(body, byteCountBegin, byteCountEnd - byteCountBegin);
				if (hex.equals("0"))
					break;
				Integer decimal = Integer.parseInt(hex, 16);
				
				int chunkedBegin = byteCountEnd + crlfByteCount;
				int chunkedEnd = chunkedBegin + decimal;
				byte[] sub = Arrays.copyOfRange(body, chunkedBegin, chunkedEnd);
				bab.append(sub, 0, sub.length);
				
				byteCountBegin = chunkedEnd + crlfByteCount;
				byteCountEnd = byteCountBegin;
			}
		}
		return bab.toByteArray();
	}
	
	private byte[] decodeGzipped(byte[] gzippedBody) {
		try {
			ByteArrayBuffer bab = new ByteArrayBuffer(gzippedBody.length);
			ByteArrayInputStream bais = new ByteArrayInputStream(gzippedBody);
			GZIPInputStream zis = new GZIPInputStream(bais);
			int read = -1;
			byte[] buf = new byte[readBufSize];
			while ((read = zis.read(buf)) != -1)
				bab.append(buf, 0, read);
			return bab.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private byte[] parseBody(byte[] rawByte) {
		String raw = new String(rawByte);
		int first = raw.indexOf(CL + CL);
		String upper = raw.substring(0, first);
		
		int bodyBegin = (upper + CL + CL).getBytes().length;
		byte[] body = Arrays.copyOfRange(rawByte, bodyBegin, rawByte.length);
		
		boolean chunked = upper.toLowerCase().contains("transfer-encoding: chunked");
		boolean gzipped = upper.toLowerCase().contains("content-encoding: gzip");
		
		if (chunked)
			body = decodeChunked(body);
		
		if (gzipped)
			body = decodeGzipped(body);
		
		return body;
	}
	
	private int parseCode(byte[] rawByte) {
		String raw = new String(rawByte);
		int first = raw.indexOf(CL);
		String status = raw.substring(0, first);
		String[] parts = status.split(" ");
		return Integer.parseInt(parts[1]);
	}
	
	protected String makeParams(StringBuffer sb) {
		Iterator<Entry<String, String>> itor = params.entrySet().iterator();
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
	
	public byte[] execute() throws IOException {
		if (conn != null && !conn.isAvaliable())
			throw new IllegalArgumentException("Connection invalid!");
		
		if (!UrlUtil.getScheme(url).equals("http"))
			throw new IllegalArgumentException("Not http scheme!");
		
		String host = (conn == null ? UrlUtil.getHost(url) : conn.getHost());
		String res = getResource(url);
		
		if (conn == null)
			conn = new Connection(host, UrlUtil.getPort(url));
		
		// headers
		BufferedOutputStream os = new BufferedOutputStream(conn.getOutPutstream());
		os.write((method() + " " + res + " HTTP/1.1" + CL).getBytes());
		os.write(("Host: " + host + CL).getBytes());
		Iterator<Entry<String, String>> itor = headers.entrySet().iterator();
		while (itor.hasNext()) {
			Entry<String, String> en = itor.next();
			os.write((en.getKey() + ": " + en.getValue() + CL).getBytes());
		}
		if (keepConnection && !headers.containsKey("Connection"))
			os.write(("Connection: Keep-Alive" + CL).getBytes());
		appendHeaders(os);
		
		os.write(CL.getBytes());
		
		// body
		if (postBody != null)
			os.write(postBody);
		
		os.flush();
		
		// response
		byte[] response = readResponse(conn);
	    
		// if should close
	    if (!keepConnection)
	    	conn.close();
	    
		return response;
	}
	
	public void addParams(Map<String, String> params) {
		this.params.putAll(params);
	}
	
	public void addParam(String key, String value) {
		params.put(key, value);
	}
	
	public void removeParam(String key) {
		params.remove(key);
	}
	
	public void clearParam() {
		params.clear();
	}
	
	public void addHeaders(Map<String, String> headers) {
		if (headers == null)
			return;
		this.headers.putAll(headers);
	}
	
	public void addHeader(String key, String value) {
		headers.put(key, value);
	}
	
	public void removeHeader(String key) {
		headers.remove(key);
	}
	
	public void clearHeader() {
		headers.clear();
	}
}
