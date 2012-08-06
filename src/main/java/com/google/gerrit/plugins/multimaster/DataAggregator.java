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

import java.util.Collection;
import java.util.LinkedList;

import com.google.gerrit.plugins.multimaster.cache.EvictNotice;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerActivity;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DataAggregator implements Evictor.Listener {
  public static class Status {
    public Peer self;
    public Long outdatedThreshold;
    public Collection<PeerActivity> activities;
    public Collection<Evict> recentEvicts;
  }

  public static class Evict {
    public String pluginName;
    public String cacheName;
    public String key;

    public Evict(String pluginName, String cacheName, String key) {
      this.pluginName = pluginName;
      this.cacheName = cacheName;
      this.key = key;
    }
  }

  private Peer self;
  private long outdatedThreshold;
  private PeerRegistry peerRegistry;

  private LinkedList<Evict> recentEvicts = new LinkedList<Evict>();

  @Inject
  public DataAggregator(@Self Peer self, PeerRegistry peerRegistry,
      @OutdatedThreshold Long threshold, Evictor evictor) {
    this.self = self;
    this.outdatedThreshold = threshold;
    this.peerRegistry = peerRegistry;

    evictor.addListener(this);
  }

  public String get() {
    Status status = new Status();
    status.self = this.self;
    status.outdatedThreshold = this.outdatedThreshold;
    status.activities = peerRegistry.getActivities();
    status.recentEvicts = recentEvicts;

    Gson gson = new Gson();

    return gson.toJson(status);
  }

  @Override
  public void onEvictNotice(final EvictNotice notice) {
    recentEvicts.addFirst(new Evict(notice.pluginName, notice.cacheName,
        notice.keyJson));

    if (recentEvicts.size() > 20) {
      recentEvicts.removeLast();
    }
  }

  @Override
  public void onOutdated() {
    // TODO Auto-generated method stub

  }

  @Override
  public void onAllOutdatedRestored() {
    // TODO Auto-generated method stub

  }

  @Override
  public void onCannotNotify(Peer target) {
    // TODO Auto-generated method stub

  }
}
