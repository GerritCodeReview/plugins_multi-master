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
