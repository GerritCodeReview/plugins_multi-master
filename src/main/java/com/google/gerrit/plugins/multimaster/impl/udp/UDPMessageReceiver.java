// Copyright (c) 2012, Code Aurora Forum. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
// * Neither the name of Code Aurora Forum, Inc. nor the names of its
// contributors may be used to endorse or promote products derived
// from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.gerrit.plugins.multimaster.impl.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.Json;
import com.google.gerrit.plugins.multimaster.impl.udp.config.MaxMessageSize;
import com.google.gerrit.plugins.multimaster.impl.udp.config.ReceiveHost;
import com.google.gerrit.plugins.multimaster.impl.udp.config.ReceivePort;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gerrit.server.util.SocketUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;

public class UDPMessageReceiver extends Thread {
  private static final Logger log = LoggerFactory
      .getLogger(UDPMessageReceiver.class);

  public interface Listener {
    public void onMessage(final UDPMessage message, final Object jsonObject);
  }

  private final Gson gson;

  private List<Listener> listeners = new LinkedList<Listener>();

  private Peer self;
  private UDPMessageSender messageSender;

  private String receiveHost;
  private InetSocketAddress receiveAddress;
  private DatagramSocket receiveSocket;

  private ExecutorService executor = Executors.newCachedThreadPool();

  private byte[] buffer;
  boolean running = false;

  @Inject
  public UDPMessageReceiver(@Self Peer self, @ReceiveHost String receiveHost,
      @ReceivePort Integer receivePort, UDPMessageSender messageSender,
      @MaxMessageSize int maxMessageSize, final Gson gson) {
    this.self = self;
    this.receiveHost = receiveHost;
    this.messageSender = messageSender;

    this.receiveAddress = SocketUtil.resolve(receiveHost, receivePort);

    this.buffer = new byte[maxMessageSize];

    this.gson = gson;

    addListener(messageSender);
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public void interrupt() {
    running = false;
    super.interrupt();
    receiveSocket.close();
  }

  @Override
  public void run() {
    try {
      receiveSocket = new DatagramSocket(receiveAddress);
    } catch (SocketException e) {
      log.error("Could not bind socket", e);
      System.exit(1);
      return;
    }

    log.info("Listening on: " + receiveHost + ":"
        + receiveSocket.getLocalPort());

    self.setProperty("receiveHost", new Json(receiveHost));
    self.setProperty("receivePort", new Json(receiveSocket.getLocalPort()));

    running = true;
    try {
      receive();
    } finally {
      if (!receiveSocket.isClosed()) {
        receiveSocket.close();
      }
    }
  }

  private void receive() {
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

    while (running) {
      try {
        receiveSocket.receive(packet);
      } catch (SocketException e) {
        log.info("Socket exception. Server is shutting down.");
        return;
      } catch (IOException e) {
        log.info("IOException", e);
        continue;
      }

      String receivedJson = new String(buffer, 0, packet.getLength()).trim();

      packet.setLength(buffer.length);

      final UDPMessage message;
      try {
        message = gson.fromJson(receivedJson, UDPMessage.class);
      } catch (JsonSyntaxException e) {
        log.error("Invalid JSON syntax", e);
        continue;
      }

      final Object jsonObject = getJsonObject(message);

      /*
       * If the contents of the message are not valid continue. This also
       * prevents an ACK from getting sent so if a message was corrupt it will
       * be resent
       */
      if (jsonObject == null) {
        switch (message.type) {
          case PULSE:
          case ACK:
            break;
          case EVICT_NOTICE:
            continue;
        }
      }

      if (message.ackRequired) {
        messageSender.submitOneshot(UDPMessage.newAck(self, message.source,
            message.id));
      }

      for (final Listener listener : listeners) {
        executor.execute(new Runnable() {
          public void run() {
            listener.onMessage(message, jsonObject);
          }
        });
      }
    }
  }

  private Object getJsonObject(UDPMessage message) {
    if (message.json == null) {
      return null;
    }

    return message.json.getObject();
  }
}
