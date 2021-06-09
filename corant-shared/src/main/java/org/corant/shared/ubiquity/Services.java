/*
 * Copyright (c) 2013-2021, Bingo.Chen (finesoft@gmail.com).
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
package org.corant.shared.ubiquity;

import static org.corant.shared.util.Classes.defaultClassLoader;
import static org.corant.shared.util.Empties.isNotEmpty;
import static org.corant.shared.util.Lists.listOf;
import static org.corant.shared.util.Objects.forceCast;
import static org.corant.shared.util.Streams.streamOf;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.corant.shared.exception.CorantRuntimeException;
import org.corant.shared.util.Classes;

/**
 * corant-shared
 *
 * @author bingo 下午10:48:27
 *
 */
public class Services {

  /**
   * Returns an {@link Optional} service instance that matches the given service class from
   * ServiceLoader and {@link Classes#defaultClassLoader()} or return an empty {@link Optional} if
   * not found.
   *
   * Note: If there are multiple service instances found by ServiceLoader and given service class is
   * {@link Sortable} then return the highest priority service instance.
   *
   * @param <T> the service type to be resolved
   * @param serviceClass the service instance class to be resolved
   *
   * @see Sortable#compare(Sortable, Sortable)
   */
  public static <T> Optional<T> find(Class<T> serviceClass) {
    List<T> list = listOf(ServiceLoader.load(serviceClass, defaultClassLoader()));
    if (isNotEmpty(list)) {
      if (list.size() == 1) {
        return Optional.of(list.get(0));
      } else if (Sortable.class.isAssignableFrom(serviceClass)) {
        return Optional.ofNullable(
            forceCast(list.stream().map(t -> (Sortable) t).min(Sortable::compare).orElse(null)));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns an {@link Optional} service instance that matches the given service class from
   * ServiceLoader and the given class loader or return an empty {@link Optional} if not found.
   *
   * Note: If there are multiple service instances found by ServiceLoader and the given service
   * class is {@link Sortable} then return the highest priority service instance.
   *
   * @param <T> the service type to be resolved
   * @param serviceClass the service instance class to be resolved
   * @param classLoader The class loader to be used to load provider-configuration files and
   *        provider classes, or null if the system class loader (or, failing that, the bootstrap
   *        class loader) is to beused
   */
  public static <T> Optional<T> find(Class<T> serviceClass, ClassLoader classLoader) {
    List<T> list = listOf(ServiceLoader.load(serviceClass, classLoader));
    if (isNotEmpty(list)) {
      if (list.size() == 1) {
        return Optional.of(list.get(0));
      } else if (Sortable.class.isAssignableFrom(serviceClass)) {
        return Optional.ofNullable(
            forceCast(list.stream().map(t -> (Sortable) t).min(Sortable::compare).orElse(null)));
      }
    }
    return Optional.empty();
  }

  /**
   * Returns a service instance that matches the given service class from ServiceLoader and
   * {@link Classes#defaultClassLoader()} or throws exception if the service not found.
   *
   * Note: If there are multiple service instances found by ServiceLoader and given service class is
   * {@link Sortable} then return the highest priority service instance.
   *
   * @param <T> the service type to be resolved
   * @param serviceClass the service instance class to be resolved
   *
   * @see Sortable#compare(Sortable, Sortable)
   */
  public static <T> T resolve(Class<T> serviceClass) {
    return find(serviceClass).orElseThrow(() -> new CorantRuntimeException(
        "Unable to resolve the service %s through the class loader %s.", serviceClass,
        defaultClassLoader()));
  }

  /**
   * Returns a service instance that matches the given service class from ServiceLoader and
   * {@link Classes#defaultClassLoader()} or throws exception if the service not found.
   *
   * Note: If there are multiple service instances found by ServiceLoader and the given service
   * class is {@link Sortable} then return the highest priority service instance.
   *
   * @param <T> the service type to be resolved
   * @param serviceClass the service instance class to be resolved
   * @param classLoader The class loader to be used to load provider-configuration files and
   *        provider classes, or null if the system class loader (or, failing that, the bootstrap
   *        class loader) is to be used
   */
  public static <T> T resolve(Class<T> serviceClass, ClassLoader classLoader) {
    return find(serviceClass, classLoader).orElseThrow(() -> new CorantRuntimeException(
        "Unable to resolve the service %s through the class loader %s.", serviceClass,
        classLoader));
  }

  /**
   * Returns a service instance stream that matches the given service class from ServiceLoader and
   * {@link Classes#defaultClassLoader()}
   *
   * @param <T> the service type to be resolved
   * @param serviceClass the service instance class to be resolved
   */
  public static <T> Stream<T> select(Class<T> serviceClass) {
    return select(serviceClass, defaultClassLoader());
  }

  /**
   * Returns a service instance stream that matches the given service class from ServiceLoader and
   * the given class loader.
   *
   * @param <T> the service type to be resolved
   * @param serviceClass the service instance class to be resolved
   * @param classLoader The class loader to be used to load provider-configuration files and
   *        provider classes, or null if the system class loader (or, failing that, the bootstrap
   *        class loader) is to be used
   */
  public static <T> Stream<T> select(Class<T> serviceClass, ClassLoader classLoader) {
    return streamOf(ServiceLoader.load(serviceClass, classLoader));
  }
}
