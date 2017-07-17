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

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.async.RingBufferLogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import com.moonlit.logfaces.appenders.Utils;


@Plugin(name = "logFaces", category = "Core", elementType = "appender", printObject = true)
public class LogfacesAppender extends AbstractAppender{
	public static final int DEFAULT_PORT = 55200;
	public static final int DEFAULT_RECONNECTION_DELAY = 5000;
	public static final int DEFAULT_QUEUE_SIZE = 500;
	public static final int DEFAULT_NOF_RETRIES = 3;
	public static final int DEFAULT_OFFER_TIMEOUT = 0;
	public static final int READ_QUEUE_TIMEOUT = 5000;
	
	protected SocketManager socketManager;
	protected String backupRef;
	protected Appender backup;
	protected BlockingQueue<LogEvent>  queue;
	private Dispatcher dispatcher;
	protected int queueSize = DEFAULT_QUEUE_SIZE;
	protected long offerTimeout = DEFAULT_OFFER_TIMEOUT;
	protected int warnOverflow;
	protected boolean locationInfo;
	private Configuration config;
	
	protected LogfacesAppender(final String name, 
			                   final Layout<? extends Serializable> layout, 
			                   final Filter filter) 
	{
		super(name, filter, layout, true);
	}

    @Override
    public void start() {
    	queue = new ArrayBlockingQueue<LogEvent>(queueSize, true);
		if(backupRef != null)
			backup = config.getAppenders().get(backupRef);
    	
		dispatcher = new Dispatcher();
		dispatcher.setName("LogfacesDispatcher");
		dispatcher.setDaemon(true);
		dispatcher.start();
		while(!dispatcher.running){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
    	
		socketManager.start();
    	super.start();
    }
	
    @Override
    public void stop() {
		// we wait until queue is flushed to server and then yild
		// until then the appender will not receive any events
    	// the call is blocked until dispatcher is done fluching the queue
    	setStopping();
		try {
			dispatcher.running = false;
			dispatcher.join();
		} catch(Exception e) {
		}
    	
		dispatcher = null;
		socketManager.stop();
        super.stop();
    }
	
    @Override
    public void append(final LogEvent event) {
    	if(event == null || !isStarted())
    		return;
    	
		event.getContextStack();
		event.getThreadName();
		event.getContextMap();
		if(locationInfo)
			event.getSource();
    	
		// if async stuff, send it directly out
    	if(event instanceof RingBufferLogEvent){
    		socketManager.send(event);
    		return;
    	}

		try {
			if(!queue.offer(event, offerTimeout, TimeUnit.MILLISECONDS)){
				if(warnOverflow++ == 0){
					LOGGER.error(String.format("logFaces: appender queue is full [%d]. If you see this message it means that queue size needs to be increased, or amount of log events decreased.", queue.size()));
					LOGGER.error((backup == null)?"logFaces: fall back is disabled":"logFaces backup appender activated; You can later import this data into the logfaces server manually.");
				}
				if(backup != null)
					backup.append(event);
				return;
			}
			warnOverflow = 0;
		}
		catch(InterruptedException e) {
		}
		catch(Exception e){
			LOGGER.error(String.format("logFaces: appender failed to append, error: %s", e.getMessage()));
		}
    }
    
    public void setQueueSize(int size){
    	this.queueSize = size;
    }

    public void setOfferTimeout(long timeout){
    	this.offerTimeout = timeout;
    }
    
    public void setSocketManager(SocketManager sm){
    	this.socketManager = sm;
    }
    
    public void setLocationInfo(boolean location){
    	this.locationInfo = location;
    }

	public void setConfig(Configuration config) {
		this.config = config;
	}

	public void setBackupRef(String backupRef) {
		this.backupRef = backupRef;
	}

	@PluginFactory
	public static LogfacesAppender createAppender(
			@PluginAttribute("name") final String name,
            @PluginAttribute("protocol") final String protocol,
			@PluginAttribute("application") final String application,
            @PluginAttribute("remoteHost") final String host,
            @PluginAttribute("port") final String portNum,
            @PluginAttribute("offerTimeout") final String offerTimeout,
            @PluginAttribute("reconnectionDelay") final String delay,
            @PluginAttribute("nofRetries") final String nofRetries,
            @PluginAttribute("locationInfo") final String location,
            @PluginAttribute("queueSize") final String queueSize,
            @PluginAttribute(value = "charset", defaultString = "UTF-8") final Charset charset,
            @PluginAttribute("backup") final String backup,
            @PluginAttribute("format") final String format,
            @PluginAttribute("compact") final String compact,
            @PluginElement("Filters") final Filter filter,
            @PluginConfiguration final Configuration config
            )
	{
		SocketManager sm = null;
		boolean locationInfo = Utils.parseBool(location, false);
		boolean compactFormat = Utils.parseBool(compact, true);
		AbstractStringLayout layout = "json".equalsIgnoreCase(format) ? 
				                                   new LogfacesJsonLayout(application, locationInfo, compactFormat, charset) :			
			                                       new LogfacesXmlLayout(application, locationInfo, charset);
		
		if(protocol == null || protocol.equalsIgnoreCase("tcp")){
			sm = new TcpManager(host, Utils.parseInt(portNum, DEFAULT_PORT), 
					                  Utils.parseInt(delay, DEFAULT_RECONNECTION_DELAY), 
					                  Utils.parseInt(nofRetries, DEFAULT_NOF_RETRIES),
					                  layout);
		}
		else{
			sm = new UdpManager(host, Utils.parseInt(portNum, DEFAULT_PORT+1), layout);
		}
		
		LogfacesAppender lfsa = new LogfacesAppender(name, layout, filter);
		lfsa.setLocationInfo(locationInfo);
		lfsa.setQueueSize(Utils.parseInt(queueSize, DEFAULT_QUEUE_SIZE));
		lfsa.setOfferTimeout(Utils.parseLong(offerTimeout, DEFAULT_OFFER_TIMEOUT));
		lfsa.setSocketManager(sm);
		lfsa.setConfig(config);
		lfsa.setBackupRef(backup);
        return lfsa;
	}
	
	class Dispatcher extends Thread{
		volatile boolean running = false;

		public void run(){
			running = true;
			LogEvent event = null;
			while(running || !queue.isEmpty()){
				try {
					if(!socketManager.isOperational()){
						Thread.sleep(500);
						continue;
					}
					
					event = queue.poll(READ_QUEUE_TIMEOUT, TimeUnit.MILLISECONDS);
					if(event == null)
						continue;
					if(!socketManager.send(event) && running)
						queue.offer(event);
				} catch(InterruptedException e) {
					break;
				}
				catch(Exception e){
					if(!running)
						break;
					LOGGER.error(String.format("logFaces appender queue processing failed, error: %s", e.getMessage()));
					continue;
				}
			}
		}		
	}
}
