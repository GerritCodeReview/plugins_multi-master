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

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.Json;
import com.google.gerrit.plugins.multimaster.cache.CacheRemovalListenerImpl;
import com.google.gerrit.plugins.multimaster.cache.EvictNotice;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gson.Gson;
import com.google.inject.Inject;


public class UDPEvictor implements Evictor, UDPMessageReceiver.Listener,
    UDPMessageSender.Listener {
  private static final Logger log = LoggerFactory.getLogger(UDPEvictor.class);

  private final Gson gson;

  protected List<Listener> listeners = new LinkedList<Listener>();

  private final Peer self;
  private final PeerRegistry peerRegistry;
  private final UDPMessageSender udpMessageSender;
  private final UDPMessageReceiver udpMessageReceiver;
  private final UDPPacemaker udpPacemaker;

  private boolean started = false;

  @Inject
  UDPEvictor(@Self Peer self, PeerRegistry peerRegistry,
      UDPMessageSender udpMessageSender, UDPMessageReceiver udpMessageReceiver,
      UDPPacemaker udpPacemaker, Gson gson) {
    this.self = self;
    this.peerRegistry = peerRegistry;
    this.udpMessageSender = udpMessageSender;
    this.udpMessageReceiver = udpMessageReceiver;
    this.udpPacemaker = udpPacemaker;
    this.gson = gson;

    CacheRemovalListenerImpl.addListener(this);
    udpMessageReceiver.addListener(this);
    udpMessageSender.addListener(this);
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public void start() {
    log.info("UDPEvictor starting.");

    udpMessageReceiver.start();
    udpMessageSender.start();
    udpPacemaker.start();

    started = true;
  }

  @Override
  public boolean stop() {
    if (!started) {
      return true;
    }

    log.info("UDPEvictor stopping.");
    boolean senderTimedOut = !udpMessageSender.stop();
    boolean pacemakerTimedOut = !udpPacemaker.stop();

    udpMessageReceiver.interrupt();
    long waitTime = 20000;
    long waitStart = System.currentTimeMillis();
    while (true) {
      try {
        udpMessageReceiver.join(waitTime);
        break;
      } catch (InterruptedException e) {
        waitTime -= System.currentTimeMillis() - waitStart;
        continue;
      }
    }

    waitTime -= System.currentTimeMillis() - waitStart;
    boolean receiverTimedOut = waitTime <= 0;

    return !senderTimedOut && !pacemakerTimedOut && !receiverTimedOut;
  }

  @Override
  public void onRemoval(EvictNotice notice) {
    for (Peer peer : peerRegistry.getActivePeers()) {
      udpMessageSender.submitMessage(new UDPMessage(self, peer,
          UDPMessage.Type.EVICT_NOTICE, new Json(notice), true));
    }
  }

  @Override
  public void onMessage(final UDPMessage message, final Object jsonObject) {
    if (message.type != UDPMessage.Type.EVICT_NOTICE
        || !(jsonObject instanceof EvictNotice)) {
      return;
    }

    EvictNotice notice = (EvictNotice) jsonObject;

    for (Listener listener : listeners) {
      listener.onEvictNotice(notice);
    }
  }

  @Override
  public void onOutdated() {
    for (Listener listener : listeners) {
      listener.onOutdated();
    }
  }

  @Override
  public void onAllRestored() {
    for (Listener listener : listeners) {
      listener.onAllOutdatedRestored();
    }
  }

  @Override
  public void onCannotSend(UDPMessage message) {
    for (Listener listener : listeners) {
      listener.onCannotNotify(message.target);
    }
  }
}
