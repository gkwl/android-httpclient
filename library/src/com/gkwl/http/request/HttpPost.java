package com.gkwl.http.request;

import java.io.OutputStream;

public class HttpPost extends Http {

	@Override
	protected String method() {
		return "POST";
	}
	
	@Override
	protected void appendHeaders(OutputStream os) {
		super.appendHeaders(os);
		if (postBody == null)
			postBody = makeParams(new StringBuffer()).getBytes();
		
		try {
			if (!headers.containsKey("Content-Length"))
				os.write(("Content-Length: " + postBody.length + CL).getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
