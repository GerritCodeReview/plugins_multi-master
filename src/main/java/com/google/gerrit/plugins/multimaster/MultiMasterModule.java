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

import org.eclipse.jgit.lib.PersonIdent;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.plugins.multimaster.cache.CacheManager;
import com.google.gerrit.plugins.multimaster.cache.CacheRemovalListenerImpl;
import com.google.gerrit.plugins.multimaster.cache.Evictor;
import com.google.gerrit.plugins.multimaster.impl.git.GitRefManager;
import com.google.gerrit.plugins.multimaster.impl.git.GitRefPeerRegistry;
import com.google.gerrit.plugins.multimaster.impl.git.RefNameBase;
import com.google.gerrit.plugins.multimaster.impl.git.RefNameBaseProvider;
import com.google.gerrit.plugins.multimaster.impl.udp.BaseRetryTime;
import com.google.gerrit.plugins.multimaster.impl.udp.MaxMessageSize;
import com.google.gerrit.plugins.multimaster.impl.udp.MaxSenders;
import com.google.gerrit.plugins.multimaster.impl.udp.MaxTimeout;
import com.google.gerrit.plugins.multimaster.impl.udp.ReceiveHost;
import com.google.gerrit.plugins.multimaster.impl.udp.ReceiveHostProvider;
import com.google.gerrit.plugins.multimaster.impl.udp.ReceivePort;
import com.google.gerrit.plugins.multimaster.impl.udp.ReceivePortProvider;
import com.google.gerrit.plugins.multimaster.impl.udp.UDPEvictor;
import com.google.gerrit.plugins.multimaster.impl.udp.UDPMessageReceiver;
import com.google.gerrit.plugins.multimaster.impl.udp.UDPMessageSender;
import com.google.gerrit.plugins.multimaster.peer.OutdatedThreshold;
import com.google.gerrit.plugins.multimaster.peer.Peer;
import com.google.gerrit.plugins.multimaster.peer.PeerRegistry;
import com.google.gerrit.plugins.multimaster.peer.Self;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.internal.UniqueAnnotations;


public class MultiMasterModule extends AbstractModule {

  @Override
  protected void configure() {
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
    bindConstant().annotatedWith(OutdatedThreshold.class).to(60000L);

    /*
     * Git Ref
     */
    bind(AllProjectsName.class).toProvider(AllProjectsNameProvider.class).in(
        Scopes.SINGLETON);
    bind(String.class).annotatedWith(RefNameBase.class).toProvider(
        RefNameBaseProvider.class);
    bind(GitRefManager.class).in(Scopes.SINGLETON);

    bind(PeerRegistry.class).to(GitRefPeerRegistry.class).in(Scopes.SINGLETON);

    /*
     * Network
     */
    bindConstant().annotatedWith(MaxSenders.class).to(4);
    bindConstant().annotatedWith(MaxMessageSize.class).to(2048);
    bindConstant().annotatedWith(MaxTimeout.class).to(10000L);
    bindConstant().annotatedWith(BaseRetryTime.class).to(500L);

    bind(ReceivePortProvider.class);
    bind(String.class).annotatedWith(ReceiveHost.class).toProvider(
        ReceiveHostProvider.class);
    bind(Integer.class).annotatedWith(ReceivePort.class).toProvider(
        ReceivePortProvider.class);

    bind(UDPMessageSender.class).in(Scopes.SINGLETON);
    bind(UDPMessageReceiver.class);

    // TODO allow multiple evictors
    bind(Evictor.class).to(UDPEvictor.class).in(Scopes.SINGLETON);

    /*
     * Caches
     */
    bind(CacheManager.class);
    bind(CacheRemovalListenerImpl.class);
    DynamicSet.bind(binder(), CacheRemovalListener.class).to(
        CacheRemovalListenerImpl.class);

    bind(DataAggregator.class).in(Scopes.SINGLETON);

    bind(StateMachine.class).in(Scopes.SINGLETON);

    bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create()).to(
        OnStartStop.class);
  }

}
