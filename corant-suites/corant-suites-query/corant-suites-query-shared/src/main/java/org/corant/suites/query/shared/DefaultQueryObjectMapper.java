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
package org.corant.suites.query.shared;

import javax.enterprise.context.ApplicationScoped;
import org.corant.suites.json.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonpCharacterEscapes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * corant-suites-query-shared
 *
 * @author bingo 下午6:48:41
 */
@ApplicationScoped
public class DefaultQueryObjectMapper implements QueryObjectMapper {

  protected ObjectMapper OM = JsonUtils.copyMapper();

  public <T> T fromJsonString(String jsonString, Class<T> type) {
    try {
      return OM.readValue(jsonString, type);
    } catch (JsonProcessingException e) {
      throw new QueryRuntimeException(e);
    }
  }

  @Override
  public String toJsonString(Object object, boolean escape, boolean pretty) {
    if (object == null) {
      return null;
    }
    final ObjectWriter writer = pretty ? OM.writerWithDefaultPrettyPrinter() : OM.writer();
    if (escape) {
      writer.with(JsonpCharacterEscapes.instance());
    }
    try {
      return writer.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new QueryRuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T toObject(Object from, Class<T> type) {
    if (from == null) {
      return null;
    } else {
      if (type.isInstance(from)) {
        return (T) from;
      } else {
        return OM.convertValue(from, type);
      }
    }
  }
}
