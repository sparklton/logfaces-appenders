/**
 * XML layout to be used with log4j logging framework.
 * This is a derivitive work of Apache log4j project and adopted for logFaces.
 * All credits go to the authors of log4j framework whose source code is re-used.

 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://logging.apache.org/log4j/1.2/license.html
 * 
 * Copyright (c) 2009 Moonlit Software Ltd.
 */

package com.moonlit.logfaces.appenders;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Hashtable;

import org.apache.log4j.Layout;
import org.apache.log4j.MDC;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

public class LogfacesLayout extends Layout {
	protected int DEFAULT_SIZE = 256;
	protected boolean locationInfo = false;
	protected String applicationName = "";
	protected String hostName = "";
	protected boolean legacyVersion = true; // anything prior log4j 1.2.15
	protected boolean jsonFormat = false;

	public LogfacesLayout(String applicationName, boolean locationInfo, boolean jsonFormat){
		this.applicationName = applicationName;
		this.locationInfo = locationInfo;
		this.jsonFormat = jsonFormat;
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} 
		catch(UnknownHostException e) {
			try {
				hostName = InetAddress.getLocalHost().getHostAddress();
			} 
			catch(Exception ex) {
			}
		}
		
		try {
			Method method = LoggingEvent.class.getMethod("getPropertyKeySet", (Class[])null);
			if(method != null)
				legacyVersion = false;
		} 
		catch(Exception e) {
		}
	}

	@Override
	public void activateOptions() {
	}

	@Override
	public boolean ignoresThrowable() {
		return false;
	}
	
	@Override
	public String format(LoggingEvent event) {
		if(jsonFormat)
			return renderJson(event);
		return renderXml(event);
	}

	protected String renderJson(LoggingEvent event){
		StringBuilder buf = new StringBuilder(DEFAULT_SIZE);
        buf.append("{");
        
		Utils.jsonAttribute(buf, "a", applicationName, true);
		Utils.jsonAttribute(buf, "h", hostName, false);
		Utils.jsonAttribute(buf, "t", ""+event.getTimeStamp(), false);
		Utils.jsonAttribute(buf, "r", event.getThreadName(), false);
		Utils.jsonAttribute(buf, "p", event.getLevel().toString(), false);
		Utils.jsonAttribute(buf, "g", event.getLoggerName(), false);
		Utils.jsonAttribute(buf, "m", event.getRenderedMessage(), false);
        
		String ndc = event.getNDC();
		if(ndc != null)
			Utils.jsonAttribute(buf, "n", ndc, false);

		String[] s = event.getThrowableStrRep();
		if(s != null){
			Utils.jsonAttribute(buf, "w", "true", false);
			Utils.jsonAttribute(buf, "i", Arrays.asList(s), Utils.EOL, false);
		}
		
		if(locationInfo){
			LocationInfo info = event.getLocationInformation();
			Utils.jsonAttribute(buf, "c", info.getClassName(), false);
			Utils.jsonAttribute(buf, "e", info.getMethodName(), false);
			Utils.jsonAttribute(buf, "f", info.getFileName(), false);
			Utils.jsonAttribute(buf, "l", ""+info.getLineNumber(), false);
		}
		
		if(!legacyVersion){
			for(Object key : event.getPropertyKeySet()){
				Object val = event.getMDC(key.toString());
				if (val != null){
					Utils.jsonAttribute(buf, "p_"+key, val.toString(), false);
				}
			}
		}
		else{
			// older versions of log4j (prior 2.1.15)
			Hashtable<?,?> mdc = MDC.getContext();
			if(mdc != null){
				for(Object key : mdc.keySet()) {
					Object val = mdc.get(key.toString());
					if (val != null) {
						Utils.jsonAttribute(buf, "p_"+key, val.toString(), false);
					}
				}
			}
		}
		
        buf.append("}");
		return buf.toString();
	}
	
	protected String renderXml(LoggingEvent event){
		StringBuilder buf = new StringBuilder(DEFAULT_SIZE);
		buf.append("<log4j:event logger=\"");
		buf.append(Transform.escapeTags(event.getLoggerName()));
		buf.append("\" timestamp=\"");
		buf.append(event.timeStamp);
		buf.append("\" level=\"");
		buf.append(event.getLevel());
		buf.append("\" thread=\"");
		buf.append(Transform.escapeTags(event.getThreadName()));
		buf.append("\">\r\n");

		buf.append("<log4j:message><![CDATA[");
		String message = event.getRenderedMessage();
		message = Utils.safeXml(message);
		Transform.appendEscapingCDATA(buf, message);
		buf.append("]]></log4j:message>\r\n");       

		String ndc = event.getNDC();
		if(ndc != null) {
			buf.append("<log4j:NDC><![CDATA[");
			buf.append(Utils.safeXml(ndc));
			buf.append("]]></log4j:NDC>\r\n");       
		}

		String[] s = event.getThrowableStrRep();
		if(s != null) {
			buf.append("<log4j:throwable><![CDATA[");
			for(int i = 0; i < s.length; i++) {
				buf.append(Utils.safeXml(s[i]));
				buf.append("\r\n");
			}
			buf.append("]]></log4j:throwable>\r\n");
		}

		if(locationInfo) { 
			LocationInfo info = event.getLocationInformation();
			buf.append("<log4j:locationInfo class=\"");
			buf.append(Transform.escapeTags(info.getClassName()));
			buf.append("\" method=\"");
			buf.append(Transform.escapeTags(info.getMethodName()));
			buf.append("\" file=\"");
			buf.append(info.getFileName());
			buf.append("\" line=\"");
			buf.append(info.getLineNumber());
			buf.append("\"/>\r\n");
		}

		buf.append("<log4j:properties>\r\n");
		buf.append("<log4j:data name=\"application\" value=\"");
		buf.append(applicationName);
		buf.append("\"/>\r\n");
		buf.append("<log4j:data name=\"hostname\" value=\"");
		buf.append(hostName);
		buf.append("\"/>\r\n");

		if(!legacyVersion){
			// after log4j 1.2.15
			for(Object k : event.getPropertyKeySet()){
				Object v = event.getMDC(k.toString());
				if (v != null){
					String key = Utils.safeXml(k.toString());
					String value = Utils.safeXml(v.toString());
					buf.append("<log4j:data name=\"");
					buf.append(Transform.escapeTags(key));
					buf.append("\" value=\"");
					buf.append(Transform.escapeTags(value));
					buf.append("\"/>\r\n");
				}
			}
		}
		else{
			// older versions of log4j (prior 2.1.15)
			Hashtable<?,?> mdc = MDC.getContext();
			if(mdc != null){
				for(Object k : mdc.keySet()) {
					Object v = mdc.get(k.toString());
					if (v != null) {
						String key = Utils.safeXml(k.toString());
						String value = Utils.safeXml(v.toString());
						buf.append("<log4j:data name=\"");
						buf.append(Transform.escapeTags(key));
						buf.append("\" value=\"");
						buf.append(Transform.escapeTags(value));
						buf.append("\"/>\r\n");
					}
				}
			}
		}

		buf.append("</log4j:properties>\r\n");
		buf.append("</log4j:event>\r\n\r\n");
		return buf.toString();
	}
}
