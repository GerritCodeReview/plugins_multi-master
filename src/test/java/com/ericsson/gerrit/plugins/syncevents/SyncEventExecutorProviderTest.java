// Copyright (C) 2016 Ericsson
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

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import com.google.gerrit.server.git.WorkQueue;

import org.easymock.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

public class SyncEventExecutorProviderTest extends EasyMockSupport {
  private WorkQueue.Executor executorMock;
  private SyncEventExecutorProvider syncEventsExecutorProvider;

  @Before
  public void setUp() throws Exception {
    executorMock = createStrictMock(WorkQueue.Executor.class);
    WorkQueue workQueueMock = createNiceMock(WorkQueue.class);
    expect(
        workQueueMock.createQueue(4, "Sync stream events [SyncEvents plugin]"))
            .andReturn(executorMock);
    Configuration configMock = createStrictMock(Configuration.class);
    expect(configMock.getThreadPoolSize()).andReturn(4);
    replayAll();
    syncEventsExecutorProvider =
        new SyncEventExecutorProvider(workQueueMock, "SyncEvents", configMock);
  }

  @Test
  public void shouldReturnExecutor() throws Exception {
    assertThat(syncEventsExecutorProvider.get()).isEqualTo(executorMock);
  }

  @Test
  public void testStop() throws Exception {
    resetAll();
    executorMock.shutdown();
    expectLastCall().once();
    executorMock.unregisterWorkQueue();
    expectLastCall().once();
    replayAll();

    syncEventsExecutorProvider.start();
    assertThat(syncEventsExecutorProvider.get()).isEqualTo(executorMock);
    syncEventsExecutorProvider.stop();
    verifyAll();
    assertThat(syncEventsExecutorProvider.get()).isNull();
  }
}
