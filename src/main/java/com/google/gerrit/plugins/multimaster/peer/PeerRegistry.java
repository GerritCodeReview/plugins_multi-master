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
