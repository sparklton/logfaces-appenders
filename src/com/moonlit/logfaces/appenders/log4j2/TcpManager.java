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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.status.StatusLogger;

public class TcpManager implements SocketManager{
	protected int nofRetries;
	protected int reconnectionDelay;
	protected List<String> hosts = new ArrayList<String>();
	protected int port;
	protected Layout<? extends Serializable> layout;
	protected Connector connector;
	protected int hostIndex = 0;
	protected InetAddress address;
	protected OutputStream oos;
	protected volatile boolean started, operational;
	protected int nofFailures = 0;
	protected long totalCount;
	protected static final Logger LOGGER = StatusLogger.getLogger();
	
	public TcpManager(String hosts, int port, int delay, int retries, Layout<? extends Serializable> layout) {
		if(hosts != null)
			this.hosts.addAll(Arrays.asList(hosts.split(",")));
		else
			this.hosts.add("localhost");
		this.port = port;
		this.layout = layout;
		this.reconnectionDelay = delay;
		this.nofRetries = retries;
	}

	@Override
	public void start(){
		if(started)
			return;
		started = true;
		reconnect();
	}
	
	@Override
	public void stop(){
		if(!started)
			return;
		started = false;
		cleanUp();
	}
	
	@Override
	public boolean isOperational() {
		return operational;
	}
	
	@Override
	public boolean send(LogEvent event){
		if(event == null || !operational)
			return false;
		try{
			String formatted = layout.toSerializable(event).toString();
			oos.write(formatted.getBytes());
			oos.flush();
			totalCount++;
			return true;
		}
		catch(IOException e){
			LOGGER.error(String.format("logFaces: appender socket write failed: %s", e.getMessage()));
			reconnect();
		}
		catch(Exception e){
			LOGGER.error(String.format("logFaces: appender general purpose error: %s", e.getMessage()));
		}
		return false;
	}

	protected void cleanUp() {
		if (oos != null) {
			try {
				oos.close();
			}
			catch (IOException e) {
				LOGGER.error(String.format("logFaces: appender failed to close socket stream: %s", e.getMessage()));
			}

			oos = null;
			operational = false;
		}

		if (connector != null) {
			connector.shutdown = true;
			connector.interrupt();
			connector = null;
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
	
	protected void reconnect() {
		oos = null;
		operational = false;
		if(connector == null && nofRetries > 0 && started) {
			address = getAddressByName(hosts.get(hostIndex));
			connector = new Connector();
			connector.setDaemon(true);
			connector.setPriority(Thread.MIN_PRIORITY);
			connector.start();
		}
	}

	class Connector extends Thread {
		boolean shutdown = false;
		
		@SuppressWarnings("resource")
		public void run() {
			while(!shutdown){
				try{
					if(nofFailures > 0)
						sleep(reconnectionDelay);
					Socket socket = new Socket(address, port);
					socket.setKeepAlive(true);
					socket.setTcpNoDelay(true);
					oos = socket.getOutputStream();
					operational = true;
					connector = null;
					return;
				} 
				catch (InterruptedException e){
					return;
				} 
				catch(Exception e){
					if(++nofFailures >= nofRetries){
						if(++hostIndex >= hosts.size())
							hostIndex = 0;
						LOGGER.error(String.format("logFaces: appender unable to connect to %s after %d retries, trying %s", address, nofRetries, hosts.get(hostIndex)));
						nofFailures = 0;
						connector = null;
						reconnect();
						return;
					}
				}
			}
		}
	}
}
