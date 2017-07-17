/**
 * TCP socket appender using XML layout to be used with log4j logging framework.
 * This is a derivitive work of Apache log4j project and adapted for logFaces.
 * All credits go to the authors of log4j framework whose source code is re-used.

 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache Software License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://logging.apache.org/log4j/1.2/license.html
 * 
 * Copyright (c) 2010 Moonlit Software Ltd.
 */

package com.moonlit.logfaces.appenders;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.AppenderAttachable;
import org.apache.log4j.spi.LoggingEvent;

public class AsyncSocketAppender extends AppenderSkeleton implements AppenderAttachable{
	public static final int DEFAULT_PORT = 55200;
	public static final int DEFAULT_RECONNECTION_DELAY = 5000;
	public static final int DEFAULT_QUEUE_SIZE = 500;
	public static final int DEFAULT_NOF_RETRIES = 3;
	public static final int DEFAULT_OFFER_TIMEOUT = 0;
	public static final int DEFAULT_SHUTDOWN_TIMEOUT = 5000;
	public static final String APPLICATION_KEY = "application";
	public static final String HOSTNAME_KEY = "hostname";

	protected String remoteHost;
	protected InetAddress address;
	protected int port = DEFAULT_PORT;
	protected OutputStream oos;
	protected boolean active;
	protected int reconnectionDelay = DEFAULT_RECONNECTION_DELAY;
	protected boolean locationInfo = true;
	protected Connector connector;
	protected String application, format;

	protected String backupFile;
	protected Appender backupAppender;
	protected BlockingQueue<LoggingEvent>  queue;
	protected LogfacesLayout layout;
	protected List<String> hosts = new ArrayList<String>();
	protected Dispatcher dispatcher;
	protected int hostIndex = 0;
	protected long offerTimeout = DEFAULT_OFFER_TIMEOUT;
	protected long shutdowdnTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
	protected int nofRetries = DEFAULT_NOF_RETRIES;
	protected int queueSize = DEFAULT_QUEUE_SIZE;
	protected int nofFailures = 0;
	protected int warnOverflow;
	protected long totalCount;

	@Override
	public boolean requiresLayout(){
		return false;
	}

	@Override
	public void activateOptions() {
		if(hosts.size() == 0)
			throw new IllegalStateException("remoteHost property is required for appender: " + name);
		layout = new LogfacesLayout(application, locationInfo, "json".equals(format));

		// backward compatibility with log4j.porperties format which doesn't support
		// appender references, the backup appender will be hard coded into RollingFileAppender
		// using 'backupFile' property 
		if(backupFile != null && backupAppender == null){
			try {
				backupAppender = new RollingFileAppender(layout, backupFile, true);
				backupAppender.setName("LFS-FALLBACK");
				((RollingFileAppender)backupAppender).activateOptions();
			} catch (Exception e) {
				backupAppender = null;
				System.err.println("logFaces: failed to init fall back appender: " + e.getMessage());
			}
		}
		
		totalCount = 0;
		queue = new ArrayBlockingQueue<LoggingEvent>(queueSize, true);
		dispatcher = new Dispatcher();
		dispatcher.setName("LogfacesDispatcher");
		dispatcher.setDaemon(true);
		dispatcher.start();
		while(!dispatcher.running){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		active = true;
		
		new Thread(new Runnable(){
			public void run() {
				connect();
			}
		}).start();
	}

	@Override
	public void close() {
		if (closed)
			return;
		closed = true;
		active = false;
		
		try {
			dispatcher.running = false;
			dispatcher.join();
			dispatcher = null;
		} catch (Exception e) {
		}
		
		removeAllAppenders();
		cleanUp();
	}
	
	public boolean isClosed(){
		return closed;
	}

	public boolean isActive() {
		return active;
	}
	
	protected void cleanUp() {
		if (oos != null) {
			try {
				oos.close();
			} catch (IOException e) {
				System.err.println(String.format("logFaces: appender failed to close socket stream: %s", e.getMessage()));
			}

			oos = null;
		}

		if (connector != null) {
			connector.shutdown = true;
			connector.interrupt();
			connector = null;
		}
	}

	@SuppressWarnings("resource")
	protected void connect() {
		try {
			cleanUp();
			address = getAddressByName(hosts.get(hostIndex));
			Socket socket = new Socket(address, port);
			socket.setKeepAlive(true);
			socket.setTcpNoDelay(true);
			oos = socket.getOutputStream();
		} 
		catch (Exception e) {
			System.err.println(String.format("logFaces: appender failed to connect to server %s:%d, starting failover", hosts.get(hostIndex), port));
			startFailover();
		}
	}
	
	@Override
	public void append(LoggingEvent event) {
		if (event == null || !active)
			return;

		try {
			// copy original information of the caling thread
			event.getNDC();
			event.getThreadName();
			event.getMDCCopy();
			if(locationInfo)
				event.getLocationInformation();
			
			// try to queue
			if(!queue.offer(event, offerTimeout, TimeUnit.MILLISECONDS)){
				if(warnOverflow++ == 0){
					System.err.println(String.format("logFaces: appender queue is full [%d]. If you see this message it means that queue size needs to be increased, or amount of log events decreased.", queue.size()));
					System.err.println( (backupAppender == null)?"logFaces: fall back is disabled":String.format("logFaces backup appender %s activated; You can later import this data into the logfaces server manually.", backupAppender.getName()));
				}
				
				// transmition queue is full, delegate to fall back appender if specified 
				if(backupAppender != null)
					backupAppender.doAppend(event);
			}
			else{
				warnOverflow = 0;
			}
		} 
		catch(InterruptedException e){
		}
		catch(Exception e){
			System.err.println(String.format("logFaces: appender failed to append, error: %s", e.getMessage()));
		}
	}

	private InetAddress getAddressByName(String host) {
		try {
			return InetAddress.getByName(host);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	protected void startFailover() {
		if (connector == null && nofRetries > 0 && active) {
			address = getAddressByName(hosts.get(hostIndex));
			System.err.println(String.format("logFaces: appender trying to fall back to %s", address));
			
			connector = new Connector();
			connector.setDaemon(true);
			connector.setPriority(Thread.MIN_PRIORITY);
			connector.start();
		}
	}
	
	class Connector extends Thread {
		volatile boolean shutdown = false;
		public void run() {
			Socket socket;
			while (!shutdown) {
				try {
					sleep(reconnectionDelay);
					socket = new Socket(address, port);
					socket.setKeepAlive(true);
					socket.setTcpNoDelay(true);
					synchronized (this) {
						oos = socket.getOutputStream();
						connector = null;
						return;
					}
				} catch (InterruptedException e) {
					return;
				} catch(Exception e) {
					if(++nofFailures >= nofRetries){
						System.err.println(String.format("logFaces: appender unable to connect to %s after %d retries", address, nofRetries));

						// fall back to next host in the list if retries are exhausted
						if(++hostIndex >= hosts.size())
							hostIndex = 0;
						nofFailures = 0;
						connector = null;
						startFailover();
						return;
					}
				}
			}
		}
	}
	
	class Dispatcher extends Thread{
		volatile boolean running = false;

		public void run(){
			LoggingEvent event;
			running = true;
			while(running || !queue.isEmpty()){
				try {
					if(oos == null){
						Thread.sleep(500);
						continue;
					}
					
					event = queue.poll(shutdowdnTimeout, TimeUnit.MILLISECONDS);
					if(event == null)
						continue;

				}catch (InterruptedException e1) {
					break;
				}
				catch(Exception e){
					if(!running)
						break;
					System.err.println(String.format("logFaces appender queue taking failed: %s", e.getMessage()));
					continue;
				}
				
				try{
					oos.write(layout.format(event).getBytes());
					oos.flush();
					totalCount++;
				}
				catch(IOException e){
				   oos = null;
				   System.err.println(String.format("logFaces appender socket write failed: %s", e.getMessage()));
				   if(!running)
					   break;
				   
				   // put it back into queue and start recovery
				   queue.offer(event);
				   System.err.println(String.format("re-queued event, queue size now %d", queue.size()));
			       startFailover();
				}
				catch(Exception e){
					if(!running)
						break;
					System.err.println(String.format("logFaces appender general purpose error: %s", e.getMessage()));
				}
			}
			
			System.err.println("logFaces appender dispatcher thread ends");
		}
	}

	public void setRemoteHost(String host) {
		remoteHost = host;
		String split[] = remoteHost.split(",");
		hosts.addAll(Arrays.asList(split));
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

	public int getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(int queueSize) {
		this.queueSize = queueSize;
	}

	public void setReconnectionDelay(int delay) {
		if(delay <= DEFAULT_RECONNECTION_DELAY)
			delay = DEFAULT_RECONNECTION_DELAY;
		this.reconnectionDelay = delay;
	}

	public int getReconnectionDelay() {
		return reconnectionDelay;
	}

	public int getNofRetries() {
		return nofRetries;
	}

	public void setNofRetries(int nofRetries) {
		this.nofRetries = nofRetries;
	}
	
	public long getOfferTimeout() {
		return offerTimeout;
	}

	public void setOfferTimeout(long offerTimeout) {
		this.offerTimeout = offerTimeout;
	}
	
	public long getShutdowdnTimeout() {
		return shutdowdnTimeout;
	}

	public void setShutdowdnTimeout(long timeout) {
		this.shutdowdnTimeout = timeout < DEFAULT_SHUTDOWN_TIMEOUT ? DEFAULT_SHUTDOWN_TIMEOUT : timeout;
	}
	
	public String getBackupFile() {
		return backupFile;
	}

	public void setBackupFile(String backupFile) {
		this.backupFile = backupFile;
	}
	
	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}
	
	//
	// custom implementation of AppenderAttachable
	// we allow only single appender to be attached to this appender
	// whatever new appender is attached/detached - it will be
	// treated as a backupAppender automatically
	//
	@Override
	public void addAppender(Appender appender) {
		backupAppender = appender;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Enumeration getAllAppenders() {
		return null;
	}

	@Override
	public Appender getAppender(String arg0) {
		return backupAppender;
	}

	@Override
	public boolean isAttached(Appender appender) {
		if(appender != null && appender.equals(backupAppender))
			return true;
		return false;
	}

	@Override
	public void removeAllAppenders() {
		if(backupAppender != null){
			backupAppender.close();
			backupAppender = null;
		}
	}

	@Override
	public void removeAppender(Appender appender) {
		if(appender == backupAppender){
			backupAppender.close();
			backupAppender = null;
		}
	}

	@Override
	public void removeAppender(String name) {
		if(backupAppender != null && backupAppender.getName().equals(name)){
			backupAppender.close();
			backupAppender = null;
		}
	}

	public long getTotalCount() {
		return totalCount;
	}
}
