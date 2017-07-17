/**
 * UDP socket appender using XML layout to be used with log4j logging framework.
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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;


public class LFUDPAppender extends AppenderSkeleton{
	private LogfacesLayout layout;
	private String remoteHost;
	private String application;
	private String encoding;
	private String format;
	private boolean locationInfo;
	private String overrideProperties = "true";
	private InetAddress address;
	private int port = 55201;
	private DatagramSocket outSocket;
	private boolean inError = false;

	public LFUDPAppender() {
	}

	public LFUDPAppender(final InetAddress address, final int port) {
		this.address = address;
		this.remoteHost = address.getHostName();
		this.port = port;
		activateOptions();
	}

	public LFUDPAppender(final String host, final int port) {
		super(false);
		this.port = port;
		this.address = getAddressByName(host);
		this.remoteHost = host;
		activateOptions();
	}

	public void activateOptions() {
		layout = new LogfacesLayout(application, locationInfo, "json".equals(format));

		if(remoteHost != null) {
			address = getAddressByName(remoteHost);
			connect(address, port);
		} else {
			String err = "logFaces appender remoteHost property is not specified";
			throw new IllegalStateException(err);
		}
		super.activateOptions();
	}

	public synchronized void close() {
		if (closed) {
			return;
		}

		this.closed = true;
		cleanUp();
	}

	public void cleanUp() {
		if (outSocket != null) {
			try {
				outSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			outSocket = null;
		}
	}

	void connect(InetAddress address, int port) {
		if (this.address == null)
			return;
		try {
			cleanUp();
			outSocket = new DatagramSocket();
			outSocket.connect(address, port);
		} catch (IOException e) {
			e.printStackTrace();
			inError = true;
		}
	}

	public void append(LoggingEvent event) {
		if(inError || event == null || address == null || outSocket == null)
			return;

		try {
			byte[] payload;
			if(encoding == null)
				payload = layout.format(event).getBytes();
			else
				payload = layout.format(event).getBytes(encoding);

			DatagramPacket dp = new DatagramPacket(payload, payload.length, address, port);
			outSocket.send(dp);
		} catch (IOException e) {
			outSocket = null;
			System.err.println("logFaces appender failed to append: " + e.getMessage());
		}
	}

	public boolean isActive() {
		return !inError;
	}

	InetAddress getAddressByName(String host) {
		try {
			return InetAddress.getByName(host);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public boolean requiresLayout() {
		return false;
	}

	public void setRemoteHost(String host) {
		remoteHost = host;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public void setApplication(String app) {
		this.application = app;
	}

	public String getApplication() {
		return application;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setOverrideProperties(String overrideProperties) {
		this.overrideProperties = overrideProperties;
	}

	public String getOverrideProperties() {
		return overrideProperties;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	public void setLocationInfo(boolean locationInfo) {
		this.locationInfo = locationInfo;
	}	

	public boolean getLocationInfo() {
		return locationInfo;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}
}