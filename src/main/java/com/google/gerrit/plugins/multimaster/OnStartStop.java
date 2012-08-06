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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.impl.git.GitRefManager;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gson.Gson;
import com.google.inject.Inject;

public class OnStartStop implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(OnStartStop.class);

  @Inject
  Gson gson;

  Peer self;

  private GitRefManager gitRefManager;
  private PeerRegistry peerRegistry;
  private Evictor evictor;
  private StateMachine stateMachine;

  @Inject
  OnStartStop(@Self Peer self, GitRefManager gitRefManager,
      PeerRegistry peerRegistry, Evictor evictor, DataAggregator aggregator,
      StateMachine stateMachine) {
    this.self = self;
    this.gitRefManager = gitRefManager;
    this.peerRegistry = peerRegistry;
    this.evictor = evictor;
    this.stateMachine = stateMachine;
  }

  @Override
  public void start() {
    gitRefManager.open();
    evictor.start();
    peerRegistry.start();
  }

  @Override
  public void stop() {
    if (!evictor.stop()) {
      log.warn("Evictor termination timed out.");
    }

    if (!stateMachine.stop()) {
      log.warn("State machine termination timed out.");
    }

    gitRefManager.close();

    if (!peerRegistry.stop()) {
      log.warn("Peer registry termination timed out.");
    }
  }
}
