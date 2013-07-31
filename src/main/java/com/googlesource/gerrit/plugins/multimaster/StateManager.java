// Copyright (c) 2013, The Linux Foundation. All rights reserved.
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
// * Neither the name of The Linux Foundation nor the names of its
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

package com.googlesource.gerrit.plugins.multimaster;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.googlesource.gerrit.plugins.multimaster.cluster.PeerRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class StateManager implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(StateManager.class);

  private volatile MemberState state;
  private volatile boolean flushed;
  private PeerRegistry peerRegistry;

  @Inject
  public StateManager(PeerRegistry peerRegistry) {
    state = new MemberState(false);
    this.peerRegistry = peerRegistry;
  }

  @Override
  public void start() {
    try {
      peerRegistry.register();
    } catch (IOException e) {
      log.warn("Could not register self", e);
    }
  }

  @Override
  public void stop() {
    // do nothing
  }

  public MemberState getMemberState() {
    return state;
  }

  public void enableAutoMode() {
    synchronized (state) {
      state = new MemberState(true);
    }
  }

  public void disableAutoMode() {
    synchronized (state) {
      state = new MemberState(false);
    }
  }

  public class MemberState {
    private final boolean auto;

    private MemberState(boolean auto) {
      this.auto = auto;
    }

    public boolean isDegraded() {
      if (auto) {
        return !flushed;
      }
      return true;
    }

    /**
     * Indicates that the caches will be flushed. Should be called at the start
     * of the flush operation.
     */
    public void flushed() {
      flushed = true;
    }
  }
}