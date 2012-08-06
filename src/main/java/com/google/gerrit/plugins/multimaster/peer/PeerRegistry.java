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

package com.google.gerrit.plugins.multimaster.peer;

import java.util.Collection;
import java.util.Set;


public interface PeerRegistry {
  public interface Listener {
    public void onPeerJoined(Peer peer);

    public void onPeerUpdated(Peer peer);

    public void onPeerLost(Peer peer);
  }

  /**
   * Start logic
   */
  abstract public void start();

  /**
   * Stop logic
   *
   * @return true if successful, false if timed out
   */
  abstract public boolean stop();

  /**
   * Add listener
   *
   * @param listener to add
   */
  public void addListener(Listener listener);

  /**
   * Remove listener
   *
   * @param listener to remove
   */
  public void removeListener(Listener listener);

  /**
   * @return the set of currently active peers
   */
  public Set<Peer> getActivePeers();

  /**
   * @return activities of all known peers (even if outdated)
   */
  public Collection<PeerActivity> getActivities();

  /**
   * Get a specific peer
   *
   * @param id of peer to fetch
   * @return peer or null if peer id is uknown
   */
  public Peer get(Peer.Id id);
}
