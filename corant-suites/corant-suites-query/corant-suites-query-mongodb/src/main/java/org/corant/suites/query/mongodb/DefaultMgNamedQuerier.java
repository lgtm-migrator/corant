package org.corant.suites.query.mongodb;
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

import com.fasterxml.jackson.core.JsonpCharacterEscapes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.bson.conversions.Bson;
import org.corant.suites.query.mongodb.MgNamedQuerier.MgOperator;
import org.corant.suites.query.shared.FetchQueryResolver;
import org.corant.suites.query.shared.QueryParameter;
import org.corant.suites.query.shared.QueryResolver;
import org.corant.suites.query.shared.QueryRuntimeException;
import org.corant.suites.query.shared.dynamic.AbstractDynamicQuerier;
import org.corant.suites.query.shared.mapping.Query;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.corant.shared.util.Empties.isNotEmpty;
import static org.corant.shared.util.ObjectUtils.forceCast;
import static org.corant.shared.util.StringUtils.asDefaultString;

/**
 * corant-suites-query
 *
 * @author bingo 下午4:35:55
 */
public class DefaultMgNamedQuerier
    extends AbstractDynamicQuerier<Map<String, Object>, EnumMap<MgOperator, Bson>>
    implements MgNamedQuerier {

  public static final ObjectMapper OM = new ObjectMapper();
  protected final String name;
  protected final EnumMap<MgOperator, Bson> script = new EnumMap<>(MgOperator.class);
  protected final EnumMap<MgOperator, List<?>> aggregateOperatorScript =
      new EnumMap<>(MgOperator.class);
  protected final String originalScript;
  protected String collectionName;

  /**
   * @param query
   * @param queryParameter
   * @param queryResolver
   * @param fetchQueryResolver
   * @param mgQuery
   * @param originalScript
   */
  protected DefaultMgNamedQuerier(
      Query query,
      QueryParameter queryParameter,
      QueryResolver resultResolver,
      FetchQueryResolver fetchQueryResolver,
      Map<?, ?> mgQuery,
      String originalScript) {
    super(query, queryParameter, resultResolver, fetchQueryResolver);
    name = query.getName();
    this.originalScript = originalScript;
    init(mgQuery);
  }

  public EnumMap<MgOperator, List<?>> getAggregateOperatorScript() {
    return aggregateOperatorScript;
  }

  @Override
  public String getCollectionName() {
    return collectionName;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getOriginalScript() {
    return originalScript;
  }

  @Override
  public EnumMap<MgOperator, Bson> getScript(Map<?, ?> additionals) {
    return script;
  }

  @Override
  public Map<String, Object> getScriptParameter() {
    return null;
  }

  /**
   * Resolve the collection name and query script
   *
   * @param mgQuery init
   */
  @SuppressWarnings("rawtypes")
  protected void init(Map<?, ?> mgQuery) {
    // resolve collection name and query script
    if (isNotEmpty(mgQuery)) {
      Entry<?, ?> entry = mgQuery.entrySet().iterator().next();
      collectionName = asDefaultString(entry.getKey());
      Map queryScript = forceCast(entry.getValue());
      if (isNotEmpty(queryScript)) {
        for (MgOperator mgo : MgOperator.values()) {
          Object x = queryScript.get(mgo.getOps());
          if (x != null) {
            try {
              switch (mgo) {
                case AGGREGATE:
                  aggregateOperatorScript.put(mgo, (List) x);
                  break;
                default:
                  script.put(
                      mgo,
                      BasicDBObject.parse(
                          OM.writer(JsonpCharacterEscapes.instance()).writeValueAsString(x)));
                  break;
              }
            } catch (Exception e) {
              throw new QueryRuntimeException(e);
            }
          }
        }
      }
    }
  }
}
