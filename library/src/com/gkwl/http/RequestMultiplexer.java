package com.gkwl.http;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.gkwl.http.listener.OnResponseListener;
import com.gkwl.http.request.Http;

/**
 *
 * @author kwl
 *
 */
public class RequestMultiplexer {
	private static class NotifyHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if (msg == null || msg.obj == null || (!(msg.obj instanceof HttpRequest)))
				return;
			
			HttpRequest request = (HttpRequest) msg.obj;
			
			if (msg.what == 0) {
				Http http = request.http;
				if (http == null || request.listener == null)
					return;
				
				request.listener.onResponse(http.getResCode(), http.getResRaw(), http.getResBody(), http.getHeaders());
			} else if (msg.what == 1) {
				if (request.listener == null)
					return;
				request.listener.onFailure(request.exception);
			}
		}
	}

	private class HttpRequest {
		public Http http;
		public OnResponseListener listener;
		public Handler handler;
		public Exception exception;
		
		public HttpRequest(Http h, OnResponseListener l) {
			this.http = h;
			this.listener = l;
			if (Looper.getMainLooper().getThread() == Thread.currentThread())
				handler = new NotifyHandler();
		}
		
		public void notifyResponse() {
			if (handler == null) {
				if (listener == null || http == null)
					return;
				listener.onResponse(http.getResCode(), http.getResRaw(), http.getResBody(), http.getHeaders());
			} else {
				handler.sendMessage(handler.obtainMessage(0, this));
			}
		}
		
		public void notifyError(Exception e) {
			exception = e;

			if (handler == null) {
				if (listener == null)
					return;
				listener.onFailure(exception);
			} else {
				handler.sendMessage(handler.obtainMessage(1, this));
			}
		}
	}
	
	private List<HandleRequestThread> threads = new LinkedList<HandleRequestThread>();
	
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
		HttpRequest request = new HttpRequest(http, onResponseListener);
		for (HandleRequestThread t : threads) {
			if (!t.isReady()) {
				t.readyRequest(request);
				t.interrupt();
				return;
			}
		}
		
		HandleRequestThread t = new HandleRequestThread(request);
		threads.add(t);
		t.start();
	}
	
	private class HandleRequestThread extends Thread {
		private Connection conn;
		private HttpRequest request;
		private boolean isReady;
		
		public HandleRequestThread(HttpRequest request) {
			this.request = request;
			isReady = true;
		}
		
		@Override
		public void run() {
			conn = new Connection();
			try {
				conn.setReadTimeout(readTimeout);
				conn.connect(host, port, connectTimeout);
				handleRequest();
			} catch (Exception e) {
				e.printStackTrace();
				request.notifyError(e);
			}
		}
		
		public void readyRequest(HttpRequest request) {
			this.request = request;
			isReady = true;
		}
		
		public boolean isReady() {
			return isReady;
		}
		
		private void handleRequest() {
			while (true) {
				
				if (request != null && request.http != null) {
					try {
						request.http.setKeepConnection(true);
						request.http.setConnection(conn);
						request.http.execute();
						request.notifyResponse();
					} catch (Exception e) {
						e.printStackTrace();
						request.notifyError(e);
						if (!(e instanceof SocketTimeoutException))
							return;
					}
				}
				
				if (!conn.isAvaliable())
					break;
				
				isReady = false;
				
				try {
					Thread.sleep(connectionTimeout);
					synchronized (RequestMultiplexer.this) {
						conn.close();
						threads.remove(this);
						return;
					}
				} catch (InterruptedException e) {
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
				
			}
		}
	}
}
