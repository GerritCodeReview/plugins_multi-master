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

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CacheFlusher implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(CacheFlusher.class);

  private final List<String> caches = Arrays.asList(new String[] {"accounts",
      "projects", "groups_members", "sshkeys"});

  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final MemberState memberState;
  private final ScheduledExecutorService scheduler;
  private ScheduledFuture<?> flusher = null;
  private final long flushRate;

  @Inject
  public CacheFlusher(DynamicMap<Cache<?, ?>> cacheMap,
      MemberState memberState, @MultiMasterConfig Config cfg) {
    this.cacheMap = cacheMap;
    this.memberState = memberState;
    scheduler = Executors.newSingleThreadScheduledExecutor();
    flushRate =
        ConfigUtil.getTimeUnit(cfg, "cache", null, "flushRate", -1,
            TimeUnit.MILLISECONDS);
  }

  @Override
  public void start() {
    if (flushRate == -1) {
      return;
    }
    flusher = scheduler.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        if (memberState.isDegraded()) {
          flushAll();
        }
      }
    }, 0, flushRate, TimeUnit.MILLISECONDS);
  }

  private void flushAll() {
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      if (caches.contains(e.getExportName())) {
        e.getProvider().get().invalidateAll();
      }
    }
  }

  @Override
  public void stop() {
    if (flusher != null) {
      flusher.cancel(false);
    }
    scheduler.shutdown();
    long waitTime = 20000;
    long waitStart = System.currentTimeMillis();
    while (waitTime > 0) {
      try {
        if (!scheduler.awaitTermination(waitTime, TimeUnit.MILLISECONDS)) {
          log.warn("Scheduler termination timed out");
        }
        return;
      } catch (InterruptedException e) {
      } finally {
        waitTime -= System.currentTimeMillis() - waitStart;
        waitStart = System.currentTimeMillis();
      }
    }
  }
}
