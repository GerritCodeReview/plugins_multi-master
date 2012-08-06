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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.plugins.multimaster.Json;
import com.google.gerrit.plugins.multimaster.MultiMasterUtil;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class UDPMessageSender implements UDPMessageReceiver.Listener {
  private static final Logger log = LoggerFactory
      .getLogger(UDPMessageSender.class);

  private final int maxMessageSize;
  private final long baseRetryTime;
  private final int maxRetries;

  public interface Listener {
    public void onCannotSend(UDPMessage message);
  }

  private List<Listener> listeners = new LinkedList<Listener>();

  private ListeningExecutorService sender;

  private Map<Integer, ScheduledFuture<?>> resendTasks = Collections
      .synchronizedMap(new HashMap<Integer, ScheduledFuture<?>>());
  private ScheduledExecutorService resender;
  private DatagramSocket sendSocket;

  @Inject
  public UDPMessageSender(@MaxTimeout long maxTimeout,
      @BaseRetryTime long baseRetryTime, @MaxSenders int maxSenders,
      @MaxMessageSize int maxMessageSize) {
    this.baseRetryTime = baseRetryTime;
    this.maxRetries = MultiMasterUtil.inverseCumFib(maxTimeout / baseRetryTime);
    this.maxMessageSize = maxMessageSize;

    this.sender =
        MoreExecutors.listeningDecorator(Executors
            .newFixedThreadPool(maxSenders));

    this.resender =
        MoreExecutors.listeningDecorator(Executors
            .newScheduledThreadPool(maxSenders));
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void start() {
    log.info("UDPMessageSender starting.");

    try {
      sendSocket = new DatagramSocket();
    } catch (SocketException e) {
      log.error("Failed to initialize socket", e);
      System.exit(1);
    }
  }

  /**
   * Attempt to gracefully shutdown sending tasks
   *
   * @return true if successful, false if timed out
   */
  public boolean stop() {
    log.info("UDPMessageSender stopping.");

    boolean senderTimedOut = false;

    sender.shutdown();
    long waitTime = 20000;
    long waitStart = System.currentTimeMillis();
    while (true) {
      try {
        senderTimedOut =
            !sender.awaitTermination(waitTime, TimeUnit.MILLISECONDS);
        break;
      } catch (InterruptedException e) {
        waitTime -= System.currentTimeMillis() - waitStart;
        waitStart = System.currentTimeMillis();
        continue;
      }
    }

    boolean resenderTimedOut = false;

    resender.shutdown();
    waitTime = 20000;
    waitStart = System.currentTimeMillis();
    while (true) {
      try {
        resenderTimedOut =
            !resender.awaitTermination(waitTime, TimeUnit.MILLISECONDS);
        break;
      } catch (InterruptedException e) {
        waitTime -= System.currentTimeMillis() - waitStart;
        waitStart = System.currentTimeMillis();
        continue;
      }
    }

    sendSocket.close();

    return !senderTimedOut && !resenderTimedOut;
  }

  /**
   * Submit a message to the executor service for sending
   *
   * @param message the message to send
   */
  public void submitMessage(final UDPMessage message)
      throws RejectedExecutionException {
    ListenableFuture<Peer> future = sender.submit(new SendTask(message));

    if (!message.ackRequired) {
      return;
    }

    scheduleRetry(message);

    Futures.addCallback(future, new FutureCallback<Peer>() {
      public void onSuccess(Peer result) {

      }

      public void onFailure(Throwable t) {
        log.info("Failed to send message. Cancelling resends.");
        ScheduledFuture<?> future = resendTasks.remove(message.id);
        if (future != null) {
          future.cancel(false);
        }

        for (Listener listener : listeners) {
          listener.onCannotSend(message);
        }
      }
    });
  }

  public void submitOneshot(final UDPMessage message) {
    sender.submit(new SendTask(message));
  }

  private void scheduleRetry(final UDPMessage message) {
    final UDPMessage retry = UDPMessage.newRetry(message);

    ScheduledFuture<?> future = resender.schedule(
        new Runnable() {
          public void run() {
            if (retry.numTries <= maxRetries) {
              submitMessage(retry);
            } else {
              log.error("Message " + retry.id + ": Giving up after "
                  + maxRetries + " tries.");
              for (Listener listener : listeners) {
                listener.onCannotSend(message);
              }
            }
          }
        }, MultiMasterUtil.fib(message.numTries + 1) * baseRetryTime,
        TimeUnit.MILLISECONDS);

    resendTasks.put(retry.id, future);
  }

  /**
   * If an ack is received cancel the resend job.
   *
   * @param id of message to cancel
   */
  public void ackReceived(int id) {
    ScheduledFuture<?> future = resendTasks.remove(id);

    if (future != null) {
      future.cancel(false);
    }
  }

  private class SendTask implements Callable<Peer> {
    private UDPMessage message;
    private Map<Peer.Id, InetSocketAddress> cachedAddresses =
        new HashMap<Peer.Id, InetSocketAddress>();

    public SendTask(UDPMessage msg) {
      this.message = msg;
    }

    public Peer call() {
      if (message.type != UDPMessage.Type.PULSE) {
        log.info("SEND:" + message.toJson());
      }

      if (message.numTries > 0) {
        log.info("Message " + message.id + ": Retry #" + message.numTries);
      }

      String json = message.toJson();

      if (json.length() > maxMessageSize) {
        log.error("Message too long to send: " + json);
        for (Listener listener : listeners) {
          listener.onCannotSend(message);
        }
      }

      byte[] data = message.toJson().getBytes();

      InetSocketAddress sendAddress = null;

      if (!cachedAddresses.containsKey(message.target.getId())) {
        sendAddress = getAddress(message.target);
        cachedAddresses.put(message.target.getId(), sendAddress);
      } else {
        sendAddress = cachedAddresses.get(message.target.getId());
      }

      DatagramPacket packet = null;
      try {
        packet = new DatagramPacket(data, data.length, sendAddress);
        sendSocket.send(packet);
      } catch (SocketException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }

      return message.target;
    }
  }

  private InetSocketAddress getAddress(Peer peer) {
    Json sendHostJson = peer.getProperty("receiveHost");
    Json sendPortJson = peer.getProperty("receivePort");
    String sendHost =
        (sendPortJson == null) ? null : (String) sendHostJson.getObject();
    Integer sendPort =
        (sendHostJson == null) ? null : (Integer) sendPortJson.getObject();

    // TODO notify admin?
    if (sendHost == null || sendPort == null) {
      log.error("Incompatible configuration detected for peer "
          + peer.getId()
          + ". All peers must have the property \"recieveHost\" and \"receivePort\" set");
      return null;
    }

    return SocketUtil.resolve(sendHost, sendPort);
  }

  @Override
  public void onMessage(final UDPMessage message, final Object jsonObject) {
    if (message.type != UDPMessage.Type.ACK) {
      return;
    }

    ackReceived(message.id);
  }
}
