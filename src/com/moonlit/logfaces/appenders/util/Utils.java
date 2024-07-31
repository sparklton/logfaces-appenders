package com.moonlit.logfaces.appenders.util;

import java.net.InetAddress;
import java.util.List;

public class Utils {
	public static final String EOL = System.getProperty("line.separator");
	public static final String APP_KEY = "application";
	public static final String HOST_KEY = "hostname";
			
    public static long parseLong(String s, long defaultValue) {
    	if(s == null)
    		return defaultValue;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int parseInt(String s, int defaultValue) {
    	if(s == null)
    		return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean parseBool(String s, boolean defaultValue) {
    	if(s == null)
    		return defaultValue;
        try {
            return Boolean.parseBoolean(s);
        } catch(Exception e) {
            return defaultValue;
        }
    }
    
    public static void jsonAttribute(StringBuilder buf, String name, String value, boolean first){
    	if(!first)
	       	buf.append(",");
        buf.append("\"");
        buf.append(safeJson(name));
        buf.append("\":\"");
        buf.append(safeJson(value));
        buf.append("\"");
    }

    public static void jsonAttribute(StringBuilder buf, String name, List<String> list, String delim, boolean first){
    	if(!first)
	       	buf.append(",");
        buf.append("\"");
        buf.append(safeJson(name));
        buf.append("\":\"");
        int size = list.size();
        for(int i=0; i<size; i++){
        	String string = list.get(i);
        	buf.append(safeJson(string));
        	if(i < size-1)
        		buf.append(delim);
        }
        
        buf.append("\"");
    }
    
    public static String safeXml(String input){
    	return input != null ? input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]|[\\ufffe-\\uffff]", "") : "";
    }
    
    public static String safeJson(String input){
    	if(input == null || input.isEmpty())
    		return"";
    	
    	try {
			String out = input;
			out = out.replace("\\", "\\\\");
			//out = out.replace("'", "\\'");
			//out = out.replace("{", "\\{");
			//out = out.replace("}", "\\}");
			out = out.replace("\"", "\\\"");
			out = out.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]|[\\ufffe-\\uffff]", "");
			return out;
		} catch (Exception e) {
			return input;
		}
    }
    
    public static String getLocalHostName(int modification) {
    	String hostName = "";
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} 
		catch(Exception e) {
			try {
				hostName = InetAddress.getLocalHost().getHostAddress();
			} 
			catch(Exception e2) {
			}
		}
    	
		if(modification == 0)
			return hostName;
		else if(modification > 0)
			return hostName.toUpperCase();
		return hostName.toLowerCase();
    }
}
