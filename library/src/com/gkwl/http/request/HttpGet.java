package com.gkwl.http.request;


public class HttpGet extends Http {
	
	@Override
	protected String method() {
		return "GET";
	}
	
	@Override
	protected String getResource(String url) {
		return super.getResource(url) + getParams();
	}

	protected String getParams() {
		return makeParams(new StringBuffer("?"));
	}
}
