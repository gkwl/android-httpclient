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
	
	public void addFile(UploadFile file) {
		this.files.add(file);
	}
	
	public void addFiles(List<UploadFile> files) {
		this.files.addAll(files);
	}
	
	@Override
	protected void appendHeaders(OutputStream os) throws NullPointerException, IOException {
		byte[] boundary = "WebKitFormBoundaryUoBKFJTqwNSCAwOp".getBytes();
		byte[] dash = "--".getBytes();
		byte[] cl = CL.getBytes();
		
		if (reqBody == null) {
			ByteArrayBuffer bab = new ByteArrayBuffer(0);
			
			// strs
			Iterator<Entry<String, String>> i = reqParams.entrySet().iterator();
			while (i.hasNext()) {
				Entry<String, String> e = i.next();
				
				bab.append(dash, 0, dash.length);
				bab.append(boundary, 0, boundary.length);
				bab.append(cl, 0, cl.length);
				
				byte[] bs = ("Content-Disposition: form-data; name=\"" + e.getKey() + "\"").getBytes();
				bab.append(bs, 0, bs.length);
				bab.append(cl, 0, cl.length);
				bab.append(cl, 0, cl.length);
				
				bs = e.getValue().getBytes();
				bab.append(bs, 0, bs.length);
				bab.append(cl, 0, cl.length);
				
				if (!i.hasNext() && files.isEmpty()) {
					bab.append(dash, 0, dash.length);
					bab.append(boundary, 0, boundary.length);
					bab.append(dash, 0, dash.length);
					bab.append(cl, 0, cl.length);
				}
			}
			
			// files
			for (UploadFile uf : files) {
				if (uf.fieldName == null || uf.upload == null)
					continue;
				
				bab.append(dash, 0, dash.length);
				bab.append(boundary, 0, boundary.length);
				bab.append(cl, 0, cl.length);
				
				byte[] bs = ("Content-Disposition: form-data; name=\"" + uf.fieldName + "\"; filename=\"" + uf.upload.getName() + "\"").getBytes();
				bab.append(bs, 0, bs.length);
				bab.append(cl, 0, cl.length);
				
				bs = ("Content-Type: " + uf.mime).getBytes();
				bab.append(bs, 0, bs.length);
				bab.append(cl, 0, cl.length);
				bab.append(cl, 0, cl.length);
				
				bs = FileUtil.toByteArray(uf.upload);
				if (bs != null)
					bab.append(bs, 0, bs.length);
				
				bab.append(cl, 0, cl.length);
			}
			
			bab.append(dash, 0, dash.length);
			bab.append(boundary, 0, boundary.length);
			bab.append(dash, 0, dash.length);

			reqBody = bab.toByteArray();
		}
		
		super.appendHeaders(os);
		os.write(("Content-Type: multipart/form-data; boundary=").getBytes());
		os.write(boundary);
		os.write(cl);
	}
}
