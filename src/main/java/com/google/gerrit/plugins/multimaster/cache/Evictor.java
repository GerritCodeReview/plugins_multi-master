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

package com.google.gerrit.plugins.multimaster.cache;

import com.google.gerrit.plugins.multimaster.peer.Pacemaker;
import com.google.gerrit.plugins.multimaster.peer.Peer;


public interface Evictor extends CacheRemovalListenerImpl.Listener,
    Pacemaker.Listener {
  public interface Listener {
    public void onEvictNotice(EvictNotice notice);

    public void onOutdated();

    public void onAllOutdatedRestored();

    public void onCannotNotify(Peer target);
  }

  public void addListener(Listener listener);

  public void removeListener(Listener listener);

  public abstract void start();

  public abstract boolean stop();
}
