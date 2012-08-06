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

package com.google.gerrit.plugins.multimaster.impl.git;

import org.eclipse.jgit.lib.Config;

import com.google.gerrit.plugins.multimaster.MultiMasterConfig;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class GitRefModule extends AbstractModule {
  private static final String CONFIG_TITLE = "GitRefPeerRegistry";

  @Override
  protected void configure() {
    bind(AllProjectsName.class).toProvider(AllProjectsNameProvider.class).in(
        Scopes.SINGLETON);
    bind(GitRefManager.class).in(Scopes.SINGLETON);
    bind(PeerRegistry.class).to(GitRefPeerRegistry.class).in(Scopes.SINGLETON);
  }

  @Provides
  @RefNameBase
  public String getRefNameBase(@MultiMasterConfig final Config config) {
    final String[] want =
        config.getStringList(CONFIG_TITLE, null, "refNameBase");

    if (want != null && want.length == 1) {
      return want[0];
    }

    return "refs/meta/cluster/";
  }
}
