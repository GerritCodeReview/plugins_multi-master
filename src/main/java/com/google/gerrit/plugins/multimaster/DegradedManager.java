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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.plugins.multimaster.cache.CacheManager;
import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DegradedManager {
  private static final Logger log = LoggerFactory
      .getLogger(DegradedManager.class);

  private ScheduledExecutorService scheduler = Executors
      .newSingleThreadScheduledExecutor();

  private volatile ScheduledFuture<?> degradedModeFuture = null;

  private CacheManager cacheManager;

  private long outdatedThreshold;

  @Inject
  public DegradedManager(CacheManager cacheManager,
      @OutdatedThreshold long outdatedThreshold) {
    this.cacheManager = cacheManager;
    this.outdatedThreshold = outdatedThreshold;
  }

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

  public synchronized void startDegraded() {
    if (degradedModeFuture != null) {
      return;
    }

    log.info("Starting degraded mode.");
    degradedModeFuture = scheduler.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        cacheManager.flushAll();
      }
    }, 0, outdatedThreshold, TimeUnit.MILLISECONDS);
  }

  public synchronized void stopDegraded() {
    if (degradedModeFuture == null) {
      return;
    }

    log.info("Stopping degraded mode.");

    degradedModeFuture.cancel(false);
    degradedModeFuture = null;
  }
}
