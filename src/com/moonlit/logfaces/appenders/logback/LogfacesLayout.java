/**
 * LogfacesLayout serializes logback events to be sent out with socket appender.
 * Can be used with XML and JSON formats.
 * Created by Moonlit Software Ltd logfaces team.
 * 
 * All credits go to the authors of logback framework whose source code is re-used.
 * This layout is free software, you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation.
 */

package com.moonlit.logfaces.appenders.logback;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Marker;

import com.moonlit.logfaces.appenders.util.Transform;
import com.moonlit.logfaces.appenders.util.Utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;

public class LogfacesLayout extends LayoutBase<ILoggingEvent> {
	private final int DEFAULT_SIZE = 256;
	private final String MARKER_CONTEXT = "marker";
	private boolean delegateMarker, locationInfo, json;
	private String applicationName = "", hostName;

	public LogfacesLayout(boolean json, String app, boolean marker, boolean location){
		this.json = json;
		this.applicationName = app;
		this.delegateMarker = marker;
		this.locationInfo = location;
		try {
			this.hostName = InetAddress.getLocalHost().getHostName();
		} 
		catch(Exception e) {
			try {
				this.hostName = InetAddress.getLocalHost().getHostAddress();
			} 
			catch(Exception e2) {
			}
		}
	}

	@Override
	public String doLayout(ILoggingEvent event) {
		return json ? doJsonLayout(event) : doXmlLayout(event);
	}
	
	public String doXmlLayout(ILoggingEvent event) {
		StringBuilder buf = new StringBuilder(DEFAULT_SIZE);
		buf.append("<log4j:event logger=\"");
		buf.append(event.getLoggerName());
		buf.append("\" timestamp=\"");
		buf.append(event.getTimeStamp());
		buf.append("\" level=\"");
		buf.append(event.getLevel());
		buf.append("\" thread=\"");
		buf.append(event.getThreadName());
		buf.append("\">\r\n");

		buf.append("  <log4j:message><![CDATA[");
		String message = event.getFormattedMessage();
		Transform.appendEscapingCDATA(buf, Utils.safeXml(message));
		buf.append("]]></log4j:message>\r\n");

		IThrowableProxy tp = event.getThrowableProxy();
		if (tp != null) {
			buf.append("  <log4j:throwable><![CDATA[");
			buf.append("\r\n");
			String ex = ThrowableProxyUtil.asString(tp);
			buf.append(Utils.safeXml(ex));
			buf.append("\r\n");
			buf.append("]]></log4j:throwable>\r\n");
		}

		if(locationInfo) {
			StackTraceElement[] callerDataArray = event.getCallerData();
			if (callerDataArray != null && callerDataArray.length > 0) {
				StackTraceElement immediateCallerData = callerDataArray[0];
				buf.append("  <log4j:locationInfo class=\"");
				buf.append(immediateCallerData.getClassName());
				buf.append("\" method=\"");
				buf.append(Transform.escapeTags(immediateCallerData.getMethodName()));
				buf.append("\" file=\"");
				buf.append(immediateCallerData.getFileName());
				buf.append("\" line=\"");
				buf.append(immediateCallerData.getLineNumber());
				buf.append("\"/>\r\n");
			}
		}

		buf.append("<log4j:properties>\r\n");
		buf.append("<log4j:data name=\"" + Utils.APP_KEY);
		buf.append("\" value=\"" + Transform.escapeTags(applicationName));
		buf.append("\"/>\r\n");

		buf.append("<log4j:data name=\"" + Utils.HOST_KEY);
		buf.append("\" value=\"" + Transform.escapeTags(hostName));
		buf.append("\"/>\r\n");
		
		if(delegateMarker){
			List<Marker> markers = event.getMarkerList();
			if(markers != null && !markers.isEmpty()) {
				buf.append("\r\n    <log4j:data");
				buf.append(" name='" + Transform.escapeTags(MARKER_CONTEXT) + "'");
				buf.append(" value='" + Transform.escapeTags(markers.get(0).getName()) + "'");
				buf.append("/>");
			}
		}
		
		Map<String, String> propertyMap = event.getMDCPropertyMap();
		if ((propertyMap != null) && (propertyMap.size() != 0)) {
			Set<Entry<String, String>> entrySet = propertyMap.entrySet();
			for (Entry<String, String> entry : entrySet) {
				buf.append("\r\n    <log4j:data");
				buf.append(" name='" + Transform.escapeTags(entry.getKey()) + "'");
				buf.append(" value='" + Transform.escapeTags(entry.getValue()) + "'");
				buf.append("/>");
			}
		}
		
		buf.append("\r\n  </log4j:properties>");
		buf.append("\r\n</log4j:event>\r\n\r\n");

		return buf.toString();
	}

	public String doJsonLayout(ILoggingEvent event) {
		StringBuilder buf = new StringBuilder(DEFAULT_SIZE);
		buf.append("{");
		
		Utils.jsonAttribute(buf, "a", applicationName, true);
		Utils.jsonAttribute(buf, "h", hostName, false);
		Utils.jsonAttribute(buf, "t", Long.toString(event.getTimeStamp()), false);
		Utils.jsonAttribute(buf, "r", event.getThreadName(), false);
		Utils.jsonAttribute(buf, "p", event.getLevel().toString(), false);
		Utils.jsonAttribute(buf, "g", event.getLoggerName(), false);
		Utils.jsonAttribute(buf, "m", event.getMessage() != null ? event.getFormattedMessage() : "", false);
		
		IThrowableProxy tp = event.getThrowableProxy();
		if (tp != null) {
			Utils.jsonAttribute(buf, "w", "true", false);
			Utils.jsonAttribute(buf, "i", ThrowableProxyUtil.asString(tp), false);
		}
		
		if(locationInfo) {
			StackTraceElement[] callerDataArray = event.getCallerData();
			if (callerDataArray != null && callerDataArray.length > 0) {
				StackTraceElement element = callerDataArray[0];
				Utils.jsonAttribute(buf, "c", element.getClassName(), false);
				Utils.jsonAttribute(buf, "e", element.getMethodName(), false);
				Utils.jsonAttribute(buf, "f", element.getFileName(), false);
				Utils.jsonAttribute(buf, "l", ""+element.getLineNumber(), false);
			}
		}

		Map<String, String> mdc = event.getMDCPropertyMap();
		if(mdc != null) {
			for(String key : mdc.keySet())
				Utils.jsonAttribute(buf, "p_"+key, String.valueOf(mdc.get(key)), false);
		}
		
		if(delegateMarker) {
			List<Marker> markers = event.getMarkerList();
			if(markers != null && !markers.isEmpty())
				Utils.jsonAttribute(buf, "p_"+MARKER_CONTEXT, markers.get(0).getName(), false);
		}
		
		buf.append("}");
		return buf.toString();
	}
}
