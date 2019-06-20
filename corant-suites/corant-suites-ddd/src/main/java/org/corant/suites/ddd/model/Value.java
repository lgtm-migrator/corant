/*
 * Copyright (c) 2013-2018, Bingo.Chen (finesoft@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.corant.suites.ddd.model;

import static org.corant.shared.util.ObjectUtils.forceCast;
import java.io.Serializable;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public interface Value extends Serializable {

  /**
   * corant-suites-ddd
   *
   * @author bingo 下午2:59:24
   *
   */
  public static class SimpleValueMap implements Value {

    private static final long serialVersionUID = -3001346370986339895L;

    public static final SimpleValueMap EMPTY_INST = new SimpleValueMap(null);

    protected final Map<String, Object> contents;

    protected SimpleValueMap(Map<String, Object> contents) {
      super();
      if (contents != null) {
        this.contents = Collections.unmodifiableMap(new HashMap<>(contents));
      } else {
        this.contents = Collections.emptyMap();
      }
    }

    public static SimpleValueMapBuilder builder() {
      return new SimpleValueMapBuilder();
    }

    public static SimpleValueMap empty() {
      return EMPTY_INST;
    }

    public <T> T get(String key) {
      return forceCast(contents.get(key));
    }

  }

  /**
   *
   * corant-suites-ddd
   *
   * @author bingo 下午2:59:29
   *
   */
  public static class SimpleValueMapBuilder {

    final Map<String, Object> contents = new HashMap<>();

    protected SimpleValueMapBuilder() {}

    public SimpleValueMapBuilder bool(String key, Boolean value) {
      return with(key, value);
    }

    public SimpleValueMapBuilder bool(String key, Collection<Boolean> value) {
      return with(key, value);
    }

    public SimpleValueMap build() {
      return new SimpleValueMap(contents);
    }

    public SimpleValueMapBuilder clear() {
      contents.clear();
      return this;
    }

    public SimpleValueMapBuilder date(String key, Collection<Date> value) {
      return with(key, value);
    }

    public SimpleValueMapBuilder date(String key, Date value) {
      return with(key, value);
    }

    public <T extends Enum<T>> SimpleValueMapBuilder enums(String key, Collection<T> value) {
      return with(key, value);
    }

    public <T extends Enum<T>> SimpleValueMapBuilder enums(String key, T value) {
      return with(key, value);
    }

    public <T extends Number> SimpleValueMapBuilder number(String key, Collection<T> value) {
      return with(key, value);
    }

    public <T extends Number> SimpleValueMapBuilder number(String key, T value) {
      return with(key, value);
    }

    public SimpleValueMapBuilder remove(String key) {
      contents.remove(key);
      return this;
    }

    public SimpleValueMapBuilder string(String key, Collection<String> value) {
      return with(key, value);
    }

    public SimpleValueMapBuilder string(String key, String value) {
      return with(key, value);
    }

    public <T extends Temporal> SimpleValueMapBuilder temporal(String key, Collection<T> value) {
      return with(key, value);
    }

    public <T extends Temporal> SimpleValueMapBuilder temporal(String key, T value) {
      return with(key, value);
    }

    public <T extends Value> SimpleValueMapBuilder value(String key, Collection<T> value) {
      return with(key, value);
    }

    public <T extends Value> SimpleValueMapBuilder value(String key, T value) {
      return with(key, value);
    }

    SimpleValueMapBuilder with(String key, Object value) {
      contents.put(key, value);
      return this;
    }
  }
}
