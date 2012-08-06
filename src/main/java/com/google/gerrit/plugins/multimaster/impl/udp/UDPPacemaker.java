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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.gerrit.plugins.multimaster.peer.Pacemaker;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerActivity;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.inject.Inject;

public class UDPPacemaker implements Pacemaker, UDPMessageReceiver.Listener {
  private static final Logger log = LoggerFactory.getLogger(UDPPacemaker.class);

  protected List<Listener> listeners = new LinkedList<Listener>();

  private Peer self;

  private PeerRegistry peerRegistry;
  private UDPMessageSender udpMessageSender;

  protected Map<Peer.Id, PeerActivity> activities =
      new HashMap<Peer.Id, PeerActivity>();
  protected Set<Peer> outdatedPeers = new HashSet<Peer>();

  private ScheduledExecutorService scheduler = Executors
      .newScheduledThreadPool(2);

  private long outdatedThreshold;
  private long refreshTime;

  @Inject
  UDPPacemaker(@Self Peer self, PeerRegistry peerRegistry,
      UDPMessageSender udpMessageSender, UDPMessageReceiver udpMessageReceiver,
      @OutdatedThreshold long outdatedThreshold) {
    this.self = self;
    this.peerRegistry = peerRegistry;
    this.udpMessageSender = udpMessageSender;

    this.outdatedThreshold = outdatedThreshold;
    this.refreshTime = outdatedThreshold / 10;

    peerRegistry.addListener(this);
    udpMessageReceiver.addListener(this);
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public void start() {
    scheduler.scheduleWithFixedDelay(new HeartBeatSender(), 0, refreshTime,
        TimeUnit.MILLISECONDS);
    scheduler.execute(new HeartBeatChecker());
  }


  @Override
  public boolean stop() {
    scheduler.shutdown();
    long waitTime = 20000;
    long waitStart = System.currentTimeMillis();
    while (true) {
      try {
        return scheduler.awaitTermination(waitTime, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        waitTime -= System.currentTimeMillis() - waitStart;
        waitStart = System.currentTimeMillis();
        continue;
      }
    }
  }

  @Override
  public void onMessage(final UDPMessage message, final Object jsonObject) {
    activities.put(message.source.getId(), new PeerActivity(message.source,
        System.currentTimeMillis()));
  }

  @Override
  public void onPeerJoined(Peer peer) {
    activities.put(peer.getId(),
        new PeerActivity(peer, System.currentTimeMillis()));
  }

  @Override
  public void onPeerUpdated(Peer peer) {
    activities.put(peer.getId(),
        new PeerActivity(peer, System.currentTimeMillis()));
  }

  @Override
  public void onPeerLost(Peer peer) {
    activities.remove(peer.getId());
  }

  /**
   * Task that sends a pulse to each known peer
   */
  private class HeartBeatSender implements Runnable {
    public void run() {
      try {
        for (Peer target : peerRegistry.getActivePeers()) {
          udpMessageSender.submitMessage(UDPMessage.newPulse(self, target));
        }
      } catch (RejectedExecutionException e) {
        log.warn("Could not queue hearbeat because server is shutting down.", e);
      }
    }
  }

  /**
   * Task that checks whether messages are being received from all known peers.
   * If a message is not received within a certain threshold an event is sent to
   * all listeners.
   */
  private class HeartBeatChecker implements Runnable {
    private long earliestPeerUpdate = Long.MAX_VALUE;

    public void run() {
      long nextRead = System.currentTimeMillis() + refreshTime;

      try {
        check();
      } catch (Exception e) {
        log.error("Uncaught exception", e);
      } finally {
        // If there is no peer then read after refreshTime otherwise read
        // 2*refreshTime after the earliest a peer was last seen.
        //
        // The objective is to attempt to not read until the next write occurs.
        // If we read when the next write is scheduled to happen it might be too
        // early, so wait a full cycle after that to give it time to be written
        // and read, but don't wait so long that we are way into the next cycle
        // either.
        if (earliestPeerUpdate < Long.MAX_VALUE) {
          nextRead = earliestPeerUpdate;
          while (nextRead <= System.currentTimeMillis()) {
            nextRead += 2 * refreshTime;
          }
        }

        schedule(nextRead - System.currentTimeMillis());
      }
    }

    private void check() {
      Set<Peer> outdatedStage = new HashSet<Peer>();

      for (Peer peer : peerRegistry.getActivePeers()) {
        PeerActivity activity = activities.get(peer.getId());
        if (System.currentTimeMillis() - activity.lastSeen > outdatedThreshold) {
          outdatedStage.add(peer);
        } else {
          if (activity.lastSeen < earliestPeerUpdate) {
            earliestPeerUpdate = activity.lastSeen;
          }
        }
      }

      if (!outdatedStage.isEmpty()) {
        for (Listener listener : listeners) {
          listener.onOutdated();
        }
      } else {
        for (Listener listener : listeners) {
          listener.onAllRestored();
        }
      }

      outdatedPeers = outdatedStage;
    }

    private void schedule(long delay) {
      if (!scheduler.isShutdown()) {
        scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
      }
    }
  }
}
