package com.gkwl.http.listener;

import java.util.List;
import java.util.Map;

public abstract class OnResponseListener {
	
	public abstract void onResponse(int code, byte[] response, byte[] body, Map<String, List<String>> headers);
	
	public abstract void onFailure(Throwable throwable);
}
