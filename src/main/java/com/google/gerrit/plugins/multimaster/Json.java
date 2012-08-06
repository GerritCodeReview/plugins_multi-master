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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class Json {
  private static final Logger log = LoggerFactory.getLogger(Json.class);

  private static final Gson GSON = new Gson();

  public String string;
  public String type;

  public Json(Object obj) {
    this.string = GSON.toJson(obj);
    this.type = obj.getClass().getName();
  }

  public Object getObject() {
    try {
      return GSON.fromJson(string, Class.forName(type));
    } catch (JsonSyntaxException e) {
      log.error("JSON Syntax exception: " + string, e);
    } catch (ClassNotFoundException e) {
      log.error("Class not found exception: " + type, e);
    }

    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Json)) {
      return false;
    }

    Json j = (Json) o;

    return string.equals(j.string) && type.equals(j.type);
  }

}
