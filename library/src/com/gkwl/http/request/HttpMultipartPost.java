package com.gkwl.http.request;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.util.ByteArrayBuffer;

import com.gkwl.http.util.FileUtil;

public class HttpMultipartPost extends HttpPost {
	public static class UploadFile {
		
		public UploadFile(String fieldName, File upload, String mime) {
			super();
			this.fieldName = fieldName;
			this.upload = upload;
			this.mime = mime;
		}
		
		public String fieldName;
		public File upload;
		public String mime;
	}
	
	private List<UploadFile> files = new LinkedList<UploadFile>();
	
	public void addFiles(List<UploadFile> files) {
		this.files.addAll(files);
	}
	
	@Override
	protected void appendHeaders(OutputStream os) {
		String real = "WebKitFormBoundaryUoBKFJTqwNSCAwOp";
		String boundary = "--" + real;
		String end = "--" + real + "--";
		if (postBody == null) {
			ByteArrayBuffer bab = new ByteArrayBuffer(0);
			
			// strs
			Iterator<Entry<String, String>> i = params.entrySet().iterator();
			while (i.hasNext()) {
				Entry<String, String> e = i.next();
				
				byte[] bs = (boundary + CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				bs = ("Content-Disposition: form-data; name=\"" + e.getKey() + "\"" + CL + CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				bs = (e.getValue() + CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				if (!i.hasNext() && files.isEmpty()) {
					bs = (end + CL).getBytes();
					bab.append(bs, 0, bs.length);
				}
			}
			
			// files
			for (UploadFile uf : files) {
				byte[] bs = (boundary + CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				bs = ("Content-Disposition: form-data; name=\"" + uf.fieldName + "\"; filename=\"" + uf.upload.getName() + "\"" + CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				bs = ("Content-Type: " + uf.mime + CL + CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				bs = FileUtil.toByteArray(uf.upload);
				if (bs != null)
					bab.append(bs, 0, bs.length);
				
				bs = (CL).getBytes();
				bab.append(bs, 0, bs.length);
				
				if (!i.hasNext()) {
					bs = (end).getBytes();
					bab.append(bs, 0, bs.length);
				}
			}

			postBody = bab.toByteArray();
		}
		
		super.appendHeaders(os);
		try {
			os.write(("Content-Type: multipart/form-data; boundary=" + real + CL).getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
