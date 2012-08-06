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

package com.google.gerrit.plugins.multimaster;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.cache.CacheManager;
import com.google.gerrit.plugins.multimaster.cache.EvictNotice;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;

public class StateMachine implements Evictor.Listener, PeerRegistry.Listener {
  private static final Logger log = LoggerFactory.getLogger(StateMachine.class);

  private final Gson gson;

  private final Peer self;

  private final long outdatedThreshold;

  private CacheManager cacheManager;
  private DegradedManager degradedManager;

  private ScheduledExecutorService scheduler = Executors
      .newSingleThreadScheduledExecutor();
  private volatile ScheduledFuture<?> hardDegradeRestoreFuture = null;

  // Should notify other peers
  private volatile boolean isHardDegraded = false;

  private volatile boolean isOutdated = false;
  private volatile boolean isPeerHardDegraded = false;

  private Set<Peer> degradedPeers = new HashSet<Peer>();

  @Inject
  public StateMachine(final @Self Peer self, final PeerRegistry peerRegistry,
      final @OutdatedThreshold long outdatedThreshold, final Evictor evictor,
      final DegradedManager degradedManager, final CacheManager cacheManager,
      final Gson gson) {
    this.self = self;
    this.outdatedThreshold = outdatedThreshold;
    this.degradedManager = degradedManager;
    this.cacheManager = cacheManager;
    this.gson = gson;

    evictor.addListener(this);
    peerRegistry.addListener(this);

    self.setProperty("hardDegraded", new Json(false));
  }

  public boolean stop() {
    boolean success = false;

    scheduler.shutdown();
    long waitTime = 20000;
    long waitStart = System.currentTimeMillis();
    while (true) {
      try {
        success = scheduler.awaitTermination(waitTime, TimeUnit.MILLISECONDS);
        break;
      } catch (InterruptedException e) {
        waitTime -= System.currentTimeMillis() - waitStart;
        waitStart = System.currentTimeMillis();
        continue;
      }
    }

    return success && degradedManager.stop();
  }

  private synchronized void updateDegraded() {
    if (isOutdated || isPeerHardDegraded || isHardDegraded) {
      degradedManager.startDegraded();
    } else {
      degradedManager.stopDegraded();
    }
  }

  @Override
  public void onCannotNotify(Peer peer) {
    isHardDegraded = true;
    updateDegraded();

    self.setProperty("hardDegraded", new Json(true));

    // If we already have a restore scheduled cancel it to schedule a new one
    if (hardDegradeRestoreFuture != null) {
      hardDegradeRestoreFuture.cancel(false);
    }

    // Recover after two outdatedThreshold periods to ensure other peers have
    // read the hard degraded state
    hardDegradeRestoreFuture = scheduler.schedule(new Runnable() {
      public void run() {
        isHardDegraded = false;
        updateDegraded();
        hardDegradeRestoreFuture = null;

        self.setProperty("hardDegraded", new Json(false));
      }
    }, 2 * outdatedThreshold, TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void onOutdated() {
    isOutdated = true;
    updateDegraded();
  }

  @Override
  public synchronized void onAllOutdatedRestored() {
    isOutdated = false;
    updateDegraded();
  }

  @Override
  public synchronized void onPeerUpdated(Peer peer) {
    // If hard degraded
    Json isDegradedProp = peer.getProperty("hardDegraded");
    Boolean isDegraded =
        (isDegradedProp == null) ? null : (Boolean) isDegradedProp.getObject();

    if (isDegradedProp == null || isDegraded == null) {
      log.error("Incompatible configuration for peer " + peer.getId()
          + ": hardDegraded property not found.");
      return;
    }

    if (isDegraded) {
      degradedPeers.add(peer);
    } else {
      degradedPeers.remove(peer);
    }

    isPeerHardDegraded = !degradedPeers.isEmpty();
    updateDegraded();
  }

  @Override
  public void onPeerJoined(Peer peer) {
    cacheManager.flushAll();
  }

  @Override
  public void onPeerLost(Peer peer) {
    cacheManager.flushAll();
  }

  @Override
  public void onEvictNotice(EvictNotice notice) {
    Object key;
    try {
      key = gson.fromJson(notice.keyJson, Class.forName(notice.keyClass));
    } catch (JsonSyntaxException e) {
      log.error("Invalid json syntax for key", e);
      return;
    } catch (ClassNotFoundException e) {
      log.error("Could not find key class", e);
      return;
    }

    cacheManager.invalidate(notice.pluginName, notice.cacheName, key);
  }
}
