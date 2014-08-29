package com.gkwl.http.util;

public class UrlUtil {
	public static String getScheme(String url) {
		if (url == null)
			throw new IllegalArgumentException();
		String[] parts = url.split("://");
		if (parts.length > 0)
			return parts[0];
		return null;
	}
	
	public static String getHost(String url) {
		if (url == null)
			throw new IllegalArgumentException();
		String[] parts = url.split("://");
		if (parts.length > 1) {
			String domain;
			int e = parts[1].indexOf('/');
			if (e == -1)
				domain = parts[1];
			else
				domain = parts[1].substring(0, e);
			
			parts = domain.split(":");
			return parts[0];
		}
		
		return null;
	}
	
	public static int getPort(String url) {
		if (url == null)
			throw new IllegalArgumentException();
		int port = 80;
		
		String[] parts = url.split("://");
		if (parts.length > 1) {
			String domain;
			int e = parts[1].indexOf('/');
			if (e == -1)
				domain = parts[1];
			else
				domain = parts[1].substring(0, e);
			
			parts = domain.split(":");
			if (parts.length > 1)
				port = Integer.parseInt(parts[1]);
		}
		
		return port;
	}
	
	public static String getResource(String url) {
		if (url == null)
			throw new IllegalArgumentException();
		String res = "/";
		
		String[] parts = url.split("://");
		if (parts.length > 1) {
			int e = parts[1].indexOf('/');
			if (e != -1)
				res = parts[1].substring(e);
		}
		
		return res;
	}
}
