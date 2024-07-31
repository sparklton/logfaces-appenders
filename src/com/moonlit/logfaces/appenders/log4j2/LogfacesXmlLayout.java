/*
 * This is a derivative work of Apache log4j project and adapted for logFaces.
 * All credits go to the authors of log4j framework whose source code is re-used.
 * 
 * ******************************************************************************** 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package com.moonlit.logfaces.appenders.log4j2;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext.ContextStack;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.core.util.Throwables;
import org.apache.logging.log4j.core.util.Transform;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import com.moonlit.logfaces.appenders.util.Utils;


public class LogfacesXmlLayout extends AbstractStringLayout{
    private boolean locationInfo;
	private String applicationName = "";
	private String hostName = "";

	protected LogfacesXmlLayout(String application, String hostName, boolean locationInfo, Charset charset){
		super(charset);
		this.locationInfo = locationInfo;
		this.applicationName = application;
		this.hostName = hostName;
	}

    @Override
    public String toSerializable(final LogEvent event) {
        final StringBuilder buf = new StringBuilder(256);

		buf.append("<log4j:event logger=\"");
		buf.append(Transform.escapeHtmlTags(event.getLoggerName()));
		buf.append("\" timestamp=\"");
		buf.append(event.getTimeMillis());
		buf.append("\" level=\"");
		buf.append(event.getLevel());
		buf.append("\" thread=\"");
		buf.append(Transform.escapeHtmlTags(event.getThreadName()));
		buf.append("\">\r\n");

		buf.append("<log4j:message><![CDATA[");
		if(event.getMessage() != null) {
			String message = event.getMessage().getFormattedMessage();
			Transform.appendEscapingCData(buf, Utils.safeXml(message));
		}
		buf.append("]]></log4j:message>\r\n");       

		ContextStack ctx = event.getContextStack();
		if(ctx != null && ctx.getDepth() > 0) {
			String ndc = event.getContextStack().toString();
			if(ndc != null){
				ndc = ndc.replaceAll("[\\[\\]]", "");
				buf.append("<log4j:NDC><![CDATA[");
				Transform.appendEscapingCData(buf, Utils.safeXml(ndc));
				buf.append("]]></log4j:NDC>\r\n");
			}
		}

		Throwable throwable = event.getThrown();
		 if (throwable != null) {
			List<String> s = Throwables.toStringList(throwable);
			buf.append("<log4j:throwable><![CDATA[");
			for (final String str : s) {
				Transform.appendEscapingCData(buf, Utils.safeXml(str));
				buf.append("\r\n");
			}
			buf.append("]]></log4j:throwable>\r\n");
		}

		if(locationInfo) { 
			StackTraceElement element = event.getSource();
			if(element != null){
				buf.append("<log4j:locationInfo class=\"");
				buf.append(Transform.escapeHtmlTags(element.getClassName()));
				buf.append("\" method=\"");
				buf.append(Transform.escapeHtmlTags(element.getMethodName()));
				buf.append("\" file=\"");
				buf.append(element.getFileName());
				buf.append("\" line=\"");
				buf.append(element.getLineNumber());
				buf.append("\"/>\r\n");
			}
		}

		buf.append("<log4j:properties>\r\n");
		buf.append("<log4j:data name=\"" + Utils.APP_KEY);
		buf.append("\" value=\"" + Transform.escapeHtmlTags(applicationName));
		buf.append("\"/>\r\n");

		buf.append("<log4j:data name=\"" + Utils.HOST_KEY);
		buf.append("\" value=\"" + Transform.escapeHtmlTags(hostName));
		buf.append("\"/>\r\n");

		if (event.getMarker() != null){
			Marker marker = event.getMarker();
			buf.append("<log4j:data name=\"" + Transform.escapeHtmlTags("marker"));
			buf.append("\" value=\"" + Transform.escapeHtmlTags(Utils.safeXml(marker.getName())));
			buf.append("\"/>\r\n");
		}
		
		ReadOnlyStringMap contextMap = event.getContextData();
		if(contextMap != null){
			Map<String, String> map = contextMap.toMap(); 
			for(String key : map.keySet()){
				String value = Utils.safeXml(String.valueOf(map.get(key)));
				buf.append("<log4j:data name=\"");
				buf.append(Transform.escapeHtmlTags(key));
				buf.append("\" value=\"");
				buf.append(Transform.escapeHtmlTags(value));
				buf.append("\"/>\r\n");
			}
		}

		buf.append("</log4j:properties>\r\n");
		buf.append("</log4j:event>\r\n\r\n");
		return buf.toString();
    }
    
    @Override
    public byte[] getHeader() {
    	return null;
    }    

    @Override
    public byte[] getFooter() {
    	return null;
    }
    
    @Override
    public Map<String, String> getContentFormat() {
        final Map<String, String> result = new HashMap<String, String>();
        result.put("dtd", "log4j-events.dtd");
        //result.put("xsd", "log4j-events.xsd");
        result.put("version", "2.0");
        return result;
    }

    @Override
    public String getContentType() {
        return "text/xml; charset=" + this.getCharset();
    }
}
