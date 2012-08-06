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

package com.google.gerrit.plugins.multimaster.impl.git;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerActivity;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;

public class GitRefPeerRegistry implements PeerRegistry {
  private static final Logger log = LoggerFactory
      .getLogger(GitRefPeerRegistry.class);

  private ScheduledExecutorService scheduler = Executors
      .newScheduledThreadPool(2);

  protected long outdatedThreshold;
  protected long refreshTime;

  protected Map<Peer.Id, PeerActivity> activities = Collections
      .synchronizedMap(new HashMap<Peer.Id, PeerActivity>());
  protected List<Listener> listeners = Collections
      .synchronizedList(new LinkedList<Listener>());

  protected ExecutorService executor = Executors.newCachedThreadPool();

  private Peer self;
  private GitRefManager gitRefManager;
  private Gson gson;

  private boolean started = false;

  @Inject
  public GitRefPeerRegistry(final @Self Peer self,
      final GitRefManager gitRefManager, final Gson gson,
      @OutdatedThreshold long outdatedThreshold) {
    this.self = self;
    this.gitRefManager = gitRefManager;
    this.gson = gson;

    this.outdatedThreshold = outdatedThreshold;
    this.refreshTime = outdatedThreshold / 10;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void start() {
    gitRefManager.open();

    scheduler.scheduleWithFixedDelay(new WriteTask(), 0, refreshTime,
        TimeUnit.MILLISECONDS);
    scheduler.execute(new ReadTask());

    started = true;
  }

  public boolean stop() {
    if (!started) {
      return true;
    }

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

    try {
      gitRefManager.delete(self.getId());
    } catch (IOException e) {
      log.error("Failed to delete self");
    }

    gitRefManager.close();

    return success;
  }

  public Set<Peer> getActivePeers() {
    Set<Peer> activePeers = new HashSet<Peer>();
    for (PeerActivity activity : activities.values()) {
      if (System.currentTimeMillis() - activity.lastSeen <= outdatedThreshold) {
        activePeers.add(activity.peer);
      }
    }

    return activePeers;
  }

  public Collection<PeerActivity> getActivities() {
    return Collections.unmodifiableCollection(activities.values());
  }

  public Peer get(Peer.Id id) {
    return activities.get(id).peer;
  }

  /**
   * Shoots the given peer in the head.
   *
   * @param peer to shoot
   */
  protected void remove(Peer.Id peerId) {
    log.info("[" + peerId + "] Peer lost. Shooting in head.");

    activities.remove(peerId);

    try {
      gitRefManager.delete(peerId);
    } catch (IOException e) {
      log.error("IOException while attempting to delete peer ref.", e);
    }
  }

  /**
   * Determines whether a peer has been added to the registry before
   *
   * @param peer to query about
   * @return if the peer is known
   */
  protected boolean peerKnown(Peer peer) {
    return activities.containsKey(peer.getId());
  }

  /**
   * Adds new peer to map and notifies listeners. The listeners are called on
   * separate threads.
   *
   * @param activity
   */
  protected void peerJoined(final PeerActivity activity) {
    activities.put(activity.peer.getId(), activity);
    for (final Listener listener : listeners) {
      executor.execute(new Runnable() {
        public void run() {
          listener.onPeerJoined(activity.peer);
        }
      });
    }
  }

  /**
   * Updated an existing  peer to map and notifies listeners. The listeners are
   * called on separate threads.
   *
   * @param activity
   */
  protected void peerUpdated(final PeerActivity activity) {
    activities.get(activity.peer.getId()).lastSeen = activity.lastSeen;
    for (final Listener listener : listeners) {
      executor.execute(new Runnable() {
        public void run() {
          listener.onPeerUpdated(activity.peer);
        }
      });
    }
  }

  /**
   * Notifies listeners of a lost peer. The listeners are called on separate
   * threads.
   *
   * @param activity
   */
  protected void peerLost(final PeerActivity activity) {
    for (final Listener listener : listeners) {
      executor.execute(new Runnable() {
        public void run() {
          listener.onPeerLost(activity.peer);
        }
      });
    }
  }

  /**
   * Routine task that writes refs to allow other peers to see that this instance
   * is still alive.
   */
  private class WriteTask implements Runnable {
    public void run() {
      boolean result;
      try {
        result =
            gitRefManager.set(self.getId(),
                gson.toJson(new PeerActivity(self, System.currentTimeMillis()))
                    .getBytes());
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return;
      }

      if (!result) {
        System.out.println("Could not write ref");
      }
    }
  }

  /**
   * Routine task that reads peer activities from the repository and updates the
   * map.
   *
   * It also checks for outdated peers immediately following the reads.
   *
   * TODO Current implementation simply shoots the peer in the head when it is
   *      outdated.
   */
  private class ReadTask implements Runnable {
    public void run() {
      try {
        read();
        check();
      } catch (RejectedExecutionException e) {
        log.warn("Server is already shutting down. Could not queue read task.");
      } catch (Throwable e) {
        log.error("Uncaught exception", e);
        System.exit(1);
      }
    }

    private void read() {
      long readStart = System.currentTimeMillis();

      /*
       * Since this read depends on all refs getting read at the same time it
       * may be possible that one blocked read will render all others as
       * outdated. Since this implementation is written with NFS in mind this
       * will likely be a problem even with separate reads.
       */
      for (byte[] refBytes : gitRefManager.getAll()) {
        String json = new String(refBytes);

        PeerActivity activity;
        try {
          activity = gson.fromJson(json, PeerActivity.class);
        } catch (JsonSyntaxException e) {
          log.error("JsonSyntaxException", e);
          continue;
        }

        if (activity.peer.getId().equals(self.getId())) {
          continue;
        }

        if (peerKnown(activity.peer)) {
          peerUpdated(activity);
        } else {
          peerJoined(activity);
        }
      }

      long readTime = System.currentTimeMillis() - readStart;

      // Do not waste time rescheduling, reread right away
      if (readTime > 2 * refreshTime) {
        log.warn("Read took too long. Re-reading.");
        read();
      }
    }

    private void check() throws JsonSyntaxException, ClassNotFoundException {
      long earliestPeerUpdate = Long.MAX_VALUE;
      List<Peer.Id> toRemove = new LinkedList<Peer.Id>();
      for (final PeerActivity activity : activities.values()) {
        // If outdated
        if (System.currentTimeMillis() - activity.lastSeen > outdatedThreshold) {
          peerLost(activity);
          toRemove.add(activity.peer.getId());
        } else {
          // Find the earliest peer (if it is not already outdated)
          if (activity.lastSeen < earliestPeerUpdate) {
            earliestPeerUpdate = activity.lastSeen;
          }
        }
      }

      for (final Peer.Id peerId : toRemove) {
        // Stonith on separate thread since the git repository or filesystem may
        // block
        executor.execute(new Runnable() {
          public void run() {
            remove(peerId);
          }
        });
      }

      // If there is no peer then read after refreshTime otherwise read
      // 2*refreshTime after the earliest a peer was last seen.
      long nextRead = System.currentTimeMillis() + refreshTime;
      if (earliestPeerUpdate < Long.MAX_VALUE) {
        nextRead = earliestPeerUpdate;
        while (nextRead < System.currentTimeMillis()) {
          nextRead += 2 * refreshTime;
        }
      }

      schedule(nextRead - System.currentTimeMillis());
    }

    private void schedule(long delay) {
      if (!scheduler.isShutdown()) {
        scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
      }
    }
  }
}
