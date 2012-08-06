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
