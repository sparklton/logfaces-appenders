/**
 * TCP socket appender using XML layout to be used with log4j logging framework.
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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * @deprecated use AsyncSocketAppender instead
 */
public class LFXMLSocketAppender extends AppenderSkeleton {
	private String remoteHost;
	private InetAddress address;
	private int port = 55200;
	private OutputStream oos;
	private int reconnectionDelay = 30000;
	private boolean locationInfo = false;
	private Connector connector;
	private String application;
	private LogfacesLayout layout;

	public LFXMLSocketAppender() {
	}
	
	public boolean requiresLayout(){
		return false;
	}

	public void activateOptions() {
		layout = new LogfacesLayout(application, locationInfo, false);
		if(remoteHost != null) {
			address = getAddressByName(remoteHost);
			connect(address, port);
		} else {
			String err = "The RemoteHost property is required for SocketAppender named "+ name;
			throw new IllegalStateException(err);
		}
	}

	public synchronized void close() {
		if (closed) {
			return;
		}

		this.closed = true;
		cleanUp();
	}

	public void cleanUp() {
		if (oos != null) {
			try {
				oos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			oos = null;
		}

		if (connector != null) {
			connector.interrupted = true;
			connector = null; // allow gc
		}
	}

	@SuppressWarnings("resource")
	void connect(InetAddress address, int port) {
		if (this.address == null) {
			return;
		}

		try {
			cleanUp();
			oos = new Socket(address, port).getOutputStream();
		} 
		catch (Exception e) {
			if (reconnectionDelay > 0)
				fireConnector(); // fire the connector thread
			e.printStackTrace();
		}
	}

	public void append(LoggingEvent event) {
		if (event == null || oos == null) {
			return;
		}

		try {
			if (locationInfo)
				event.getLocationInformation();
			oos.write(layout.format(event).getBytes());
		} 
		catch (IOException e){
			oos = null;
			e.printStackTrace();

			if (reconnectionDelay > 0)
				fireConnector();
		}
	}

	void fireConnector() {
		if (connector == null) {
			connector = new Connector();
			connector.setDaemon(true);
			connector.setPriority(Thread.MIN_PRIORITY);
			connector.start();
		}
	}

	InetAddress getAddressByName(String host) {
		try {
			return InetAddress.getByName(host);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public void setRemoteHost(String host) {
		remoteHost = host;
	}

	public String getRemoteHost() {
		return remoteHost;
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

	public void setApplication(String lapp) {
		this.application = lapp;
	}

	public String getApplication() {
		return application;
	}

	public void setReconnectionDelay(int delay) {
		this.reconnectionDelay = delay;
	}

	public int getReconnectionDelay() {
		return reconnectionDelay;
	}

	class Connector extends Thread {
		boolean interrupted = false;

		public void run() {
			Socket socket;

			while (!interrupted) {
				try {
					sleep(reconnectionDelay);
					socket = new Socket(address, port);
					synchronized (this) {
						oos = socket.getOutputStream();
						connector = null;
						break;
					}
				} 
				catch (InterruptedException e) {
					return;
				} 
				catch (java.net.ConnectException e) {
				} 
				catch (IOException e) {
				}
			}
		}
	}
}
