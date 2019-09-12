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
package org.corant.shared.conversion.converter.factory;

import static org.corant.shared.util.CollectionUtils.immutableSetOf;
import static org.corant.shared.util.MapUtils.getMapString;
import static org.corant.shared.util.ObjectUtils.asString;
import static org.corant.shared.util.ObjectUtils.defaultObject;
import static org.corant.shared.util.StringUtils.asDefaultString;
import static org.corant.shared.util.StringUtils.isBlank;
import static org.corant.shared.util.StringUtils.split;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.corant.shared.conversion.ConversionException;
import org.corant.shared.conversion.Converter;
import org.corant.shared.conversion.ConverterFactory;

/**
 * corant-shared
 *
 * @author bingo 上午10:15:06
 *
 */
public class ObjectEnumConverterFactory implements ConverterFactory<Object, Enum<?>> {

  final Logger logger = Logger.getLogger(this.getClass().getName());
  final Set<Class<?>> supportedSourceClass = immutableSetOf(Enum.class, Number.class,
      CharSequence.class, Integer.TYPE, Long.TYPE, Short.TYPE, Byte.TYPE);

  @Override
  public Converter<Object, Enum<?>> create(Class<Enum<?>> targetClass, Enum<?> defaultValue,
      boolean throwException) {
    return (t, h) -> {
      Enum<?> result = null;
      try {
        result = convert(t, targetClass, h);
      } catch (Exception e) {
        if (throwException) {
          throw new ConversionException(e);
        } else {
          logger.warning(() -> String.format("Can not convert %s", asString(t)));
        }
      }
      return defaultObject(result, defaultValue);
    };
  }

  @Override
  public boolean isSupportSourceClass(Class<?> sourceClass) {
    return supportedSourceClass.contains(sourceClass)
        || supportedSourceClass.stream().anyMatch(c -> c.isAssignableFrom(sourceClass));
  }

  @Override
  public boolean isSupportTargetClass(Class<?> targetClass) {
    return Enum.class.isAssignableFrom(targetClass);
  }

  @SuppressWarnings("rawtypes")
  protected <T extends Enum<?>> T convert(Object value, Class<T> targetClass, Map<String, ?> hints)
      throws Exception {
    if (value instanceof Enum<?> && value.getClass().isAssignableFrom(targetClass)) {
      return targetClass.cast(value);
    } else if (value instanceof Number) {
      return targetClass.getEnumConstants()[Number.class.cast(value).intValue()];
    } else {
      String name = null;
      if (value instanceof Map) {
        name = getMapString((Map) value, "name");
      } else {
        String valueString = asDefaultString(value);
        if (isBlank(valueString)) {
          return null;
        }
        String[] values = split(valueString, ".", true, true);
        name = values[values.length - 1];
      }
      if (name != null) {
        for (T t : targetClass.getEnumConstants()) {
          if (t.name().equalsIgnoreCase(name)) {
            return t;
          }
        }
      }
      throw new ConversionException("Can not convert %s -> %s", value.getClass(), targetClass);
    }
  }
}
