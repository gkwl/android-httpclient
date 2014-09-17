package com.gkwl.http.request;


public class HttpGet extends Http {
	
	@Override
	protected String method() {
		return "GET";
	}
	
	@Override
	protected String getResource() throws NullPointerException {
		return super.getResource() + makeParams(new StringBuffer("?"));
	}
}
