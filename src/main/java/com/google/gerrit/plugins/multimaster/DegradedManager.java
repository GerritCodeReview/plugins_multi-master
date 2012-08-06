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
