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
package org.corant.config.resolve;

import static org.corant.shared.util.CollectionUtils.listOf;
import static org.corant.shared.util.ConversionUtils.toList;
import static org.corant.shared.util.ConversionUtils.toObject;
import static org.corant.shared.util.Empties.isEmpty;
import static org.corant.shared.util.Empties.isNotEmpty;
import static org.corant.shared.util.StreamUtils.streamOf;
import static org.corant.shared.util.StringUtils.split;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.corant.config.ConfigUtils;
import org.corant.config.resolve.DeclarativeConfigResolver.ConfigField;
import org.corant.shared.exception.NotSupportedException;
import org.eclipse.microprofile.config.Config;

/**
 * corant-config
 *
 * @author bingo 下午8:05:35
 *
 */
public enum DeclarativePattern {

  SUFFIX() {
    @Override
    public <T extends DeclarativeConfig> void resolve(Config config, String infix, T configObject,
        ConfigField configField) throws Exception {
      Class<?> fieldType = configField.getType();
      Field field = configField.getField();
      String key = configField.getKey(infix);
      boolean nocfg = true;
      if (fieldType.equals(Boolean.class) || fieldType.equals(Boolean.TYPE)) {
        Optional<?> val = config.getOptionalValue(key, fieldType);
        if (val.isPresent()) {
          field.set(configObject, toObject(val.get(), fieldType));
        } else {
          field.set(configObject, Boolean.FALSE);
        }
        nocfg = false;
      } else if (fieldType.equals(Optional.class)) {
        Class<?> fieldValueType = ConfigUtils.getFieldActualTypeArguments(field, 0);
        Optional<?> val = config.getOptionalValue(key, fieldValueType);
        if (val.isPresent()) {
          field.set(configObject, Optional.of(toObject(val.get(), fieldValueType)));
          nocfg = false;
        }
      } else if (Collection.class.isAssignableFrom(fieldType)) {
        Class<?> fieldValueType = ConfigUtils.getFieldActualTypeArguments(field, 0);
        Optional<String> val = config.getOptionalValue(key, String.class);
        if (val.isPresent()) {
          List<?> vals = toList(listOf(split(val.get(), ",", true, true)), fieldValueType);
          if (isNotEmpty(vals)) {
            if (fieldType.equals(List.class)) {
              field.set(configObject, vals);
            } else if (fieldType.equals(Set.class)) {
              field.set(configObject, new HashSet<>(vals));
            } else {
              throw new NotSupportedException("Can not resolve config field %s %s %s %s.",
                  configObject.getClass().getName(), field.getName(), fieldType.getName(),
                  fieldValueType.getName());
            }
            nocfg = false;
          }
        }
      } else {
        Optional<?> val = config.getOptionalValue(key, String.class);
        if (val.isPresent()) {
          field.set(configObject, toObject(val.get(), fieldType));
          nocfg = false;
        }
      }
      if (nocfg) {
        resolveNoConfig(config, configObject, configField);
      }
    }
  },

  PREFIX() {
    @Override
    public <T extends DeclarativeConfig> void resolve(Config config, String infix, T configObject,
        ConfigField configField) throws Exception {
      String key = configField.getKey(infix);
      int prefixLen = key.length();
      Map<String, Optional<String>> map =
          streamOf(config.getPropertyNames()).filter(p -> p.startsWith(key))
              .collect(Collectors.toMap(k -> k, v -> config.getOptionalValue(v, String.class)));
      if (isEmpty(map)) {
        resolveNoConfig(config, configObject, configField);
      } else {
        Class<?> filedType = configField.getType();
        Field field = configField.getField();
        Map<String, Object> valueMap = new HashMap<>();
        if (filedType.equals(Map.class)) {
          for (Entry<String, Optional<String>> entry : map.entrySet()) {
            entry.getValue().ifPresent(v -> {
              valueMap.put(ConfigUtils.removeSplitor(entry.getKey().substring(prefixLen)), v);
            });
          }
          field.set(configObject, valueMap);
        }
      }
    }
  };

  public abstract <T extends DeclarativeConfig> void resolve(Config config, String infix,
      T configObject, ConfigField configField) throws Exception;

  protected <T extends DeclarativeConfig> void resolveNoConfig(Config config, T configObject,
      ConfigField configField) throws Exception {
    String defaultValue = configField.getDefaultValue();
    Class<?> filedType = configField.getType();
    Field field = configField.getField();
    if (defaultValue != null) {
      if (filedType.equals(List.class)) {
        Class<?> listType = ConfigUtils.getFieldActualTypeArguments(field, 0);
        String[] parts = ConfigUtils.splitValue(defaultValue);
        List<Object> list = new ArrayList<>();
        for (String i : parts) {
          list.add(toObject(i, listType));
        }
        field.set(configObject, list);
      } else if (filedType.equals(Set.class)) {
        Class<?> listType = ConfigUtils.getFieldActualTypeArguments(field, 0);
        String[] parts = ConfigUtils.splitValue(defaultValue);
        Set<Object> list = new HashSet<>();
        for (String i : parts) {
          list.add(toObject(i, listType));
        }
        field.set(configObject, list);
      } else if (field.getType().equals(Optional.class)) {
        Class<?> optionalType = ConfigUtils.getFieldActualTypeArguments(field, 0);
        field.set(configObject, Optional.of(toObject(defaultValue, optionalType)));
      } else {
        field.set(configObject, toObject(defaultValue, field.getType()));
      }
    } else if (field.getType().equals(Optional.class)) {
      field.set(configObject, Optional.empty());
    } else if (field.getType().equals(OptionalInt.class)) {
      field.set(configObject, OptionalInt.empty());
    } else if (field.getType().equals(OptionalDouble.class)) {
      field.set(configObject, OptionalDouble.empty());
    } else if (field.getType().equals(OptionalLong.class)) {
      field.set(configObject, OptionalLong.empty());
    }
  }
}
