package com.gkwl.http;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

public class Connection {
	private Socket socket;
	private String host;
	private int port;
	
	public Connection() {
		socket = new Socket();
	}
	
	public Connection(String host, int port) throws IOException {
		socket = new Socket();
		connect(host, port);
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
	
	public boolean isAvaliable() {
		return socket != null && socket.isConnected() && !socket.isClosed();
	}
	
	public void close() throws IOException {
		socket.close();
	}
	
	public void connect(String host, int port) throws IOException {
		connect(host, port, 0);
	}
	
	public void connect(String host, int port, int timeout) throws IOException {
		this.host = host;
		this.port = port;
		SocketAddress remoteAddr = new InetSocketAddress(host, port);
		socket.connect(remoteAddr, timeout);
	}
	
	public void setReadTimeout(int timeoutMili) throws SocketException {
		socket.setSoTimeout(timeoutMili);
	}
	
	public OutputStream getOutPutstream() throws IOException {
		return socket.getOutputStream();
	}
	
	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}
}
