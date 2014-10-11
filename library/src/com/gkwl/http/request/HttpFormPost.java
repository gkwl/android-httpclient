package com.gkwl.http.request;

import java.io.IOException;
import java.io.OutputStream;

public class HttpFormPost extends HttpPost {
	@Override
	protected void appendHeaders(OutputStream os) throws NullPointerException, IOException {
		super.appendHeaders(os);
		if (!reqHeaders.containsKey("Content-Length"))
			os.write(("Content-Type: application/x-www-form-urlencoded" + CL).getBytes());
	}
}
