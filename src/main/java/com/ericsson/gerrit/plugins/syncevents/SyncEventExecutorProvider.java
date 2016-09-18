// Copyright (C) 2015 Ericsson
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

package com.ericsson.gerrit.plugins.syncevents;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.concurrent.ScheduledThreadPoolExecutor;

@Singleton
class SyncEventExecutorProvider
    implements Provider<ScheduledThreadPoolExecutor>, LifecycleListener {
  private WorkQueue.Executor executor;

  @Inject
  SyncEventExecutorProvider(WorkQueue workQueue,
      @PluginName String pluginName,
      Configuration config) {
    executor = workQueue.createQueue(config.getThreadPoolSize(),
        "Sync stream events [" + pluginName + " plugin]");
  }

  @Override
  public void start() {
    // do nothing
  }

  @Override
  public void stop() {
    executor.shutdown();
    executor.unregisterWorkQueue();
    executor = null;
  }

  @Override
  public ScheduledThreadPoolExecutor get() {
    return executor;
  }
}