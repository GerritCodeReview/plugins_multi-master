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
import com.google.gerrit.plugins.multimaster.peer.ClusterProperty;
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

  private final CacheManager cacheManager;
  private final DegradedManager degradedManager;
  private final PeerRegistry peerRegistry;

  private ScheduledExecutorService scheduler = Executors
      .newSingleThreadScheduledExecutor();
  private volatile ScheduledFuture<?> hardDegradeRestoreFuture = null;

  // Should notify other peers
  private volatile boolean isHardDegraded = false;

  private volatile boolean isOutdated = false;
  private volatile boolean isPeerHardDegraded = false;

  private volatile boolean isReplicationMaster = false;

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
    this.peerRegistry = peerRegistry;
    this.gson = gson;

    evictor.addListener(this);
    peerRegistry.addListener(this);

    self.setProperty("hardDegraded", new Json(false));
  }

  public void start() {
    initReplicationMaster();
  }

  private void initReplicationMaster() {
    final String name = "replicationMaster";

    if (!peerRegistry.isClusterPropertySet(name)) {
      peerRegistry.setClusterProperty(name, new Json(self.getId()));
    }

    peerRegistry.addClusterPropertyListener(name,
        new ClusterProperty.Listener() {

          @Override
          public void onUpdate(ClusterProperty property) {
            Peer.Id masterId = (Peer.Id) property.getValue().getObject();

            if (!isReplicationMaster && masterId.equals(self.getId())) {
              log.info("Became replication master.");
              isReplicationMaster = true;
            }
          }

          @Override
          public void onOutdated(ClusterProperty property) {
            log.info("Current replication master is outdated. "
                + "Attempting to become replication master.");
            peerRegistry.setClusterProperty(property.getName(),
                new Json(self.getId()));
          }

          @Override
          public void onDoesNotExist(ClusterProperty property) {
            log.info("Replication master does not exist. "
                + "Attempting to become replication master.");
            peerRegistry.setClusterProperty(property.getName(),
                new Json(self.getId()));
          }
        });
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
