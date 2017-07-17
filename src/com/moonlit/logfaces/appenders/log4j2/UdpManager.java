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
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;

public class UdpManager implements SocketManager{
	protected DatagramSocket ds;
    protected InetAddress address;
    protected int port;
    protected Layout<? extends Serializable> layout;
	
	public UdpManager(String host, int port, Layout<? extends Serializable> layout){
		this.layout = layout;
        this.port = port;
        try {
        	host = (host != null) ? host : "localhost";
            address = InetAddress.getByName(host);
        } catch (final UnknownHostException ex) {
            throw new AppenderLoggingException("Could not find host " + host, ex);
        }

        try {
            ds = new DatagramSocket();
        } catch (final SocketException ex) {
            throw new AppenderLoggingException("Could not instantiate DatagramSocket to " + host, ex);
        }
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public boolean isOperational() {
		return true;
	}
	
	@Override
	public boolean send(LogEvent event) {
		try {
			String formatted = layout.toSerializable(event).toString();
			byte[] data = formatted.getBytes();
			DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
			ds.send(packet);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
