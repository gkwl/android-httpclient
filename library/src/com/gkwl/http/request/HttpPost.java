package com.gkwl.http.request;

import java.io.IOException;
import java.io.OutputStream;

public class HttpPost extends Http {

	@Override
	protected String method() {
		return "POST";
	}
	
	@Override
	protected void appendHeaders(OutputStream os) throws NullPointerException, IOException {
		super.appendHeaders(os);
		if (reqBody == null)
			reqBody = makeParams(new StringBuffer()).getBytes();
		
		if (!reqHeaders.containsKey("Content-Length"))
			os.write(("Content-Length: " + reqBody.length + CL).getBytes());
	}
}
