package com.gkwl.http;

import java.io.IOException;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.gkwl.http.listener.OnResponseListener;
import com.gkwl.http.request.Http;

/**
 * One connection only now
 * @author kwl
 *
 */
public class RequestMultiplexer {
	private class HttpRequest {
		public Http http;
		public OnResponseListener listener;
		public Handler handler;
		
		public HttpRequest(Http h, OnResponseListener l) {
			this.http = h;
			this.listener = l;
			if (Looper.getMainLooper().getThread() != Thread.currentThread())
				Looper.prepare();
			handler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					super.handleMessage(msg);
					if (msg.what == 0) {
						listener.onResponse(http.getResCode(), http.getResRaw(), http.getResBody(), http.getHeaders());
					} else {
						Exception e = (Exception) msg.obj;
						listener.onFailure(e);
					}
				}
			};
		}
	}
	
	private Thread thread;
	private Queue<HttpRequest> requests = new LinkedList<HttpRequest>();
	
	private String host;
	private int port;
	
	private int readTimeout = 30000;
	private int connectTimeout = 10000;
	private long connectionTimeout = 10000;
	
	public RequestMultiplexer(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public void setConnectionTimeout(long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}

	public synchronized void put(Http http, OnResponseListener onResponseListener) {
		requests.offer(new HttpRequest(http, onResponseListener));
		
		if (thread == null || !thread.isAlive()) {
			thread = new HandleRequestThread();
			thread.start();
		} else {
			thread.interrupt();
		}
	}
	
	private class HandleRequestThread extends Thread {
		private Connection conn;
		
		@Override
		public void run() {
			conn = new Connection();
			try {
				conn.setReadTimeout(readTimeout);
				conn.connect(host, port, connectTimeout);
				handleRequests();
			} catch (IOException e) {
				e.printStackTrace();
				broadcastError(e);
			}
		}
		
		private void broadcastError(Exception e) {
			while (!requests.isEmpty()) {
				HttpRequest request = requests.poll();
				request.handler.sendMessage(request.handler.obtainMessage(1, e));
			}
		}
		
		private void handleRequests() {
			while (true) {
				
				while (!requests.isEmpty()) {
					HttpRequest request = requests.poll();
					try {
						request.http.setKeepConnection(true);
						request.http.setConnection(conn);
						request.http.execute();
						request.handler.sendEmptyMessage(0);
					} catch (IOException e) {
						e.printStackTrace();
						request.handler.sendMessage(request.handler.obtainMessage(1, e));
					}
				}
				
				try {
					Thread.sleep(connectionTimeout);
					synchronized (RequestMultiplexer.this) {
						try {
							conn.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						break;
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}
	}
}
