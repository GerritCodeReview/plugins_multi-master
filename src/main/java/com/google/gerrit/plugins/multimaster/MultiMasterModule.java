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

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.plugins.multimaster.cache.CacheManager;
import com.google.gerrit.plugins.multimaster.cache.CacheRemovalListenerImpl;
import com.google.gerrit.plugins.multimaster.impl.git.GitRefModule;
import com.google.gerrit.plugins.multimaster.impl.udp.UDPModule;
import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.internal.UniqueAnnotations;


public class MultiMasterModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ConfigProvider.class).in(Scopes.SINGLETON);
    bind(Config.class).annotatedWith(MultiMasterConfig.class).toProvider(
        ConfigProvider.class);

    install(new GitRefModule());
    install(new UDPModule());

    bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class).toProvider(
        GerritPersonIdentProvider.class);

    /*
     * General
     */
    bind(Gson.class).toInstance(new Gson());

    bind(DegradedManager.class).in(Scopes.SINGLETON);

    /*
     * Peers
     */
    bind(Peer.class).annotatedWith(Self.class).toInstance(new Peer());

    /*
     * Caches
     */
    bind(CacheManager.class);
    bind(CacheRemovalListenerImpl.class);
    DynamicSet.bind(binder(), CacheRemovalListener.class).to(
        CacheRemovalListenerImpl.class);

    bind(StateMachine.class).in(Scopes.SINGLETON);

    bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create()).to(
        OnStartStop.class);
  }

  @Provides
  @OutdatedThreshold
  public Long getOutdatedThreshold(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList("General", null, "outdatedThreshold");

    if (want != null && want.length == 1) {
      return Long.parseLong(want[0]);
    }

    return 60000L;
  }

}
