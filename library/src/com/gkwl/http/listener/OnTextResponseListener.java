package com.gkwl.http.listener;

import java.util.List;
import java.util.Map;

public abstract class OnTextResponseListener extends OnResponseListener {

	@Override
	public void onResponse(int code, byte[] response, byte[] body, Map<String, List<String>> headers) {
		onResponse(code, new String(response), new String(body), headers);
	}

	public abstract void onResponse(int code, String body, String response, Map<String, List<String>> headers);
}