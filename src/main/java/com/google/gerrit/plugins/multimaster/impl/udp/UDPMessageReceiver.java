// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
