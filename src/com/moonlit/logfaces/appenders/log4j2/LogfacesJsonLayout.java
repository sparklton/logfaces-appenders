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
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import com.moonlit.logfaces.appenders.util.Utils;


public class LogfacesJsonLayout extends AbstractStringLayout{
	protected boolean locationInfo;
	protected String applicationName = "";
	protected String hostName = "";
	
	protected LogfacesJsonLayout(String application, String hostName, boolean locationInfo, Charset charset){
		super(charset);
		this.locationInfo = locationInfo;
		this.applicationName = application;
		this.hostName = hostName;
	}

    @Override
    public String toSerializable(final LogEvent event) {
        StringBuilder buf = new StringBuilder(256);
        buf.append("{");
        
		Utils.jsonAttribute(buf, "a", applicationName, true);
		Utils.jsonAttribute(buf, "h", hostName, false);
		Utils.jsonAttribute(buf, "t", ""+event.getTimeMillis(), false);
		Utils.jsonAttribute(buf, "r", event.getThreadName(), false);
		Utils.jsonAttribute(buf, "p", event.getLevel().toString(), false);
		Utils.jsonAttribute(buf, "g", event.getLoggerName(), false);
		Utils.jsonAttribute(buf, "m", event.getMessage() != null ? event.getMessage().getFormattedMessage() : "", false);

		ContextStack ctx = event.getContextStack();
		if(ctx != null && ctx.getDepth() > 0)
			Utils.jsonAttribute(buf, "n", ctx.asList(), " ", false);
		
		Throwable throwable = event.getThrown();
		if (throwable != null){
			List<String> list = Throwables.toStringList(throwable);
			Utils.jsonAttribute(buf, "w", "true", false);
			Utils.jsonAttribute(buf, "i", list, Utils.EOL, false);
		}
		
        Marker marker = event.getMarker();
        if(marker != null)
        	Utils.jsonAttribute(buf, "p_marker", Utils.safeJson(marker.getName()), false);
        
		if(locationInfo) { 
			StackTraceElement element = event.getSource();
			if(element != null){
				Utils.jsonAttribute(buf, "c", element.getClassName(), false);
				Utils.jsonAttribute(buf, "e", element.getMethodName(), false);
				Utils.jsonAttribute(buf, "f", element.getFileName(), false);
				Utils.jsonAttribute(buf, "l", ""+element.getLineNumber(), false);
			}
		}

		ReadOnlyStringMap cmap = event.getContextData();
		if(cmap != null && !cmap.isEmpty()){
			Map<String,String> map = cmap.toMap();
			for(String key : map.keySet()){
				Utils.jsonAttribute(buf, "p_"+key, String.valueOf(map.get(key)), false);
			}
		}
		
        buf.append("}");
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
        return "text/json; charset=" + this.getCharset();
    }
}
