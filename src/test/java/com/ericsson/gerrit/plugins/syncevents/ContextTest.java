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

import static com.google.common.truth.Truth.assertThat;

import org.easymock.EasyMockSupport;
import org.junit.Test;

import com.ericsson.gerrit.plugins.syncevents.Context;

public class ContextTest extends EasyMockSupport {

  @Test
  public void testInitialValueNotNull() throws Exception {
    assertThat(Context.isForwardedEvent()).isNotNull();
    assertThat(Context.isForwardedEvent()).isFalse();
  }

  @Test
  public void testSetForwardedEvent() throws Exception {
    Context.setForwardedEvent();
    try {
      assertThat(Context.isForwardedEvent()).isTrue();
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  @Test
  public void testUnsetForwardedEvent() throws Exception {
    Context.setForwardedEvent();
    Context.unsetForwardedEvent();
    assertThat(Context.isForwardedEvent()).isFalse();
  }
}