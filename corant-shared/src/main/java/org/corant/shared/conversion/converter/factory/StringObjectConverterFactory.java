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

import static org.corant.shared.util.ObjectUtils.forceCast;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.corant.shared.conversion.Converter;
import org.corant.shared.conversion.ConverterFactory;

/**
 * corant-shared
 *
 * <ul>
 * <li>the target type {@code T} has a {@code public static T of(String)} method, or</li>
 * <li>the target type {@code T} has a {@code public static T valueOf(String)} method, or</li>
 * <li>the target type {@code T} has a public Constructor with a String parameter, or</li>
 * <li>the target type {@code T} has a {@code public static T parse(CharSequence)} method</li>
 * </ul>
 *
 * Unfinish yet
 *
 * @author bingo 上午11:38:58
 *
 */
public class StringObjectConverterFactory implements ConverterFactory<String, Object> {

  @Override
  public Converter<String, Object> create(Class<Object> targetClass, Object defaultValue,
      boolean throwException) {
    return forType(targetClass).get();
  }

  @Override
  public boolean isSupportTargetClass(Class<?> targetClass) {
    return forType(targetClass).isPresent();
  }

  <T> Converter<String, T> forConstructor(Class<?> targetClass, Class<?>... argumentTypes) {
    try {
      Constructor<?> constructor = targetClass.getConstructor(argumentTypes);
      if (Modifier.isPublic(constructor.getModifiers())) {
        return (s, m) -> {
          try {
            return forceCast(constructor.newInstance(s));
          } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
              | InvocationTargetException ex) {
            throw new IllegalArgumentException("Unable to convert value to type  for value " + s,
                ex);
          }
        };
      } else {
        return null;
      }
    } catch (NoSuchMethodException | SecurityException e) {
      return null;
    }
  }

  <T> Converter<String, T> forMethod(Class<?> targetClass, String method,
      Class<?>... argumentTypes) {
    try {
      Method factoryMethod = targetClass.getMethod(method, String.class);
      if (Modifier.isStatic(factoryMethod.getModifiers())
          && Modifier.isPublic(factoryMethod.getModifiers())) {
        return (s, m) -> {
          try {
            return forceCast(factoryMethod.invoke(null, s));
          } catch (IllegalAccessException | IllegalArgumentException
              | InvocationTargetException ex) {
            throw new IllegalArgumentException("Unable to convert value to type  for value " + s,
                ex);
          }
        };
      } else {
        return null;
      }
    } catch (NoSuchMethodException | SecurityException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  <T> Optional<Converter<String, T>> forType(Type generalType) {
    if (!(generalType instanceof Class)) {
      return Optional.empty();
    }
    Class<T> type = (Class<T>) generalType;
    return Stream.<Supplier<Converter<String, T>>>of(() -> forConstructor(type, String.class),
        () -> forMethod(type, "of", String.class), () -> forMethod(type, "valueOf", String.class),
        () -> forMethod(type, "parse", CharSequence.class)).map(Supplier::get)
        .filter(converter -> converter != null).findFirst();
  }

}
