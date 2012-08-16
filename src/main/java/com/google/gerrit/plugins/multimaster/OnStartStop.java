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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.impl.git.GitRefManager;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.inject.Inject;

public class OnStartStop implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(OnStartStop.class);

  private final GitRefManager gitRefManager;
  private final PeerRegistry peerRegistry;
  private final Evictor evictor;
  private final StateMachine stateMachine;

  @Inject
  OnStartStop(GitRefManager gitRefManager, PeerRegistry peerRegistry,
      Evictor evictor, StateMachine stateMachine) {
    this.gitRefManager = gitRefManager;
    this.peerRegistry = peerRegistry;
    this.evictor = evictor;
    this.stateMachine = stateMachine;
  }

  @Override
  public void start() {
    evictor.start();
    peerRegistry.start();
    stateMachine.start();
  }

  @Override
  public void stop() {
    if (!evictor.stop()) {
      log.warn("Evictor termination timed out.");
    }

    if (!stateMachine.stop()) {
      log.warn("State machine termination timed out.");
    }

    if (!peerRegistry.stop()) {
      log.warn("Peer registry termination timed out.");
    }
  }
}
