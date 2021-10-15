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
package org.corant.shared.conversion;

import static org.corant.shared.util.Assertions.shouldNotNull;
import static org.corant.shared.util.Iterables.iterableOf;
import static org.corant.shared.util.Iterables.transform;
import static org.corant.shared.util.Objects.forceCast;
import static org.corant.shared.util.Objects.tryCast;
import static org.corant.shared.util.Primitives.wrap;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.corant.shared.exception.NotSupportedException;
import org.corant.shared.ubiquity.TypeLiteral;
import org.corant.shared.util.Classes;
import org.corant.shared.util.Objects;

/**
 * corant-shared
 *
 * @author bingo 上午11:32:01
 *
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class Conversion {

  private static final Logger LOGGER = Logger.getLogger(Conversion.class.getName());

  /**
   * Convert collection value to target collection value
   *
   * @param <T> the target class of item of the collection
   * @param <C> the target collection class
   * @param value the collection value to convert
   * @param collectionFactory the constructor of collection
   * @param targetItemClass the target item class of the converted collection
   * @param hints the converter hints use for intervening converters
   * @return A collection of items converted according to the target type
   */
  public static <T, C extends Collection<T>> C convert(Collection<?> value,
      IntFunction<C> collectionFactory, Class<T> targetItemClass, Map<String, ?> hints) {
    if (value == null) {
      return null;
    }
    C collection = collectionFactory.apply(value.size());
    Iterator<T> it = convert(value, targetItemClass, hints).iterator();
    while (it.hasNext()) {
      collection.add(it.next());
    }
    return collection;
  }

  /**
   * Convert iterable values with knowed source class and target class
   *
   * @param <T> the target class of item
   * @param value the value to convert
   * @param sourceClass the knowed item source class
   * @param targetClass the item target class that will be convertted
   * @param hints the converter hints use for intervening converters
   * @return An iterable of items converted according to the target type
   */
  public static <T> Iterable<T> convert(Iterable<?> value, Class<?> sourceClass,
      Class<T> targetClass, Map<String, ?> hints) {
    Converter converter = resolveConverter(sourceClass, targetClass);
    if (converter != null) {
      return converter.iterable(value, hints);
    } else {
      Converter stringConverter = resolveConverter(String.class, targetClass);
      if (stringConverter != null) {
        LOGGER.fine(() -> String.format(
            "Can not find proper convert for %s -> %s, use String -> %s converter!", sourceClass,
            targetClass, targetClass));
        return stringConverter.iterable(transform(value, Objects::asString), hints);
      }
    }
    throw new ConversionException("Can not find converter for type pair s% -> %s.", sourceClass,
        targetClass);
  }

  /**
   * Convert iterable values with unknown source class and target class
   *
   * @param <T> the target class of item
   * @param value the value to convert
   * @param targetClass the item target class that will be converted
   * @param hints the converter hints use for intervening converters
   * @return An iterable of items converted according to the target type
   */
  public static <T> Iterable<T> convert(Iterable<?> value, Class<T> targetClass,
      Map<String, ?> hints) {
    if (value == null) {
      return null;
    }
    return () -> new Iterator<>() {
      final Iterator<?> it = value.iterator();
      Converter converter = null;
      Class<?> sourceClass = null;
      boolean tryStringConverter = false;

      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public T next() {
        Object next = it.next();
        if (next == null) {
          return null;
        }
        final Class<?> nextClass = next.getClass();
        if (converter == null || sourceClass == null || !sourceClass.equals(nextClass)) {
          converter = resolveConverter(nextClass, targetClass);
          sourceClass = nextClass;
          if (converter == null) {
            tryStringConverter = true;
            converter = resolveConverter(String.class, targetClass);
          } else {
            tryStringConverter = false;
          }
        }
        return (T) shouldNotNull(converter, "Can not find converter for type pair s% -> %s.",
            sourceClass, targetClass).apply(tryStringConverter ? next.toString() : next, hints);
      }

      @Override
      public void remove() {
        it.remove();
      }
    };
  }

  /**
   * Convert objects to certain item types and collection types, use the default collection
   * constructor.
   *
   * @param <C> the target collection class
   * @param <T> the target class of item of the collection
   * @param value the value to convert
   * @param collectionClass the collection class
   * @param targetClass the item class
   * @param hints the converter hints use for intervening converters
   * @return A collection of items converted according to the target type
   */
  public static <C extends Collection<T>, T> C convert(Object value, Class<C> collectionClass,
      Class<T> targetClass, Map<String, ?> hints) {
    return convert(value, targetClass, t -> {
      try {
        return collectionClass.getDeclaredConstructor(int.class).newInstance(t);
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
          | InvocationTargetException | NoSuchMethodException | SecurityException e) {
        throw new NotSupportedException();
      }
    }, hints);
  }

  /**
   * Convert single object to target class object without hints
   *
   * @param <T> the target class
   * @param value the value to convert
   * @param targetClass the target class that convert to
   *
   * @return the converted object
   */
  public static <T> T convert(Object value, Class<T> targetClass) {
    return convert(value, targetClass, null);
  }

  /**
   * Convert an value object to a collection objects, use for converting the
   * iterable/array/iterator/enumeration objects to collection objects.
   *
   * if the value object not belong to above then regard the value object as the first item of
   * collection and convert it.
   *
   * @param <T> the target class of item of the collection
   * @param <C> the target collection class
   * @param value the value to convert, may be an iterable/array/iterator/enumeration typed object
   * @param targetItemClass the target class of item of the collection
   * @param collectionFactory the constructor of collection
   * @param hints the converter hints use for intervening converters
   * @return A collection of items converted according to the target type
   */
  public static <T, C extends Collection<T>> C convert(Object value, Class<T> targetItemClass,
      IntFunction<C> collectionFactory, Map<String, ?> hints) {
    Iterable<T> it = null;
    int size = 10;
    if (value instanceof Iterable) {
      if (value instanceof Collection<?>) {
        size = ((Collection<?>) value).size();
      }
      it = convert(tryCast(value, Iterable.class), targetItemClass, hints);
    } else if (value instanceof Object[]) {
      Object[] array = (Object[]) value;
      size = array.length;
      it = convert(iterableOf(array), targetItemClass, hints);
    } else if (value instanceof Iterator) {
      it = convert(() -> ((Iterator) value), targetItemClass, hints);
    } else if (value instanceof Enumeration) {
      it = convert(iterableOf((Enumeration) value), targetItemClass, hints);
    } else if (value != null) {
      if (value.getClass().isArray()) {
        size = Array.getLength(value);
        Object[] array = new Object[size];
        for (int i = 0; i < size; i++) {
          array[i] = Array.get(value, i);
        }
        it = convert(iterableOf(array), targetItemClass, hints);
      } else {
        it = convert(iterableOf(value), targetItemClass, hints);
      }
    }
    final C collection = collectionFactory.apply(size);
    if (it != null) {
      for (T item : it) {
        collection.add(item);
      }
    }
    return collection;
  }

  /**
   * Convert single object to target class object with hints
   *
   * @param <S> the source value object class
   * @param <T> the target class
   * @param value the value to convert
   * @param targetClass the target class that convert to
   * @param hints the converter hints use for intervening converters
   * @return the converted object
   */
  public static <S, T> T convert(Object value, Class<T> targetClass, Map<String, ?> hints) {
    if (value == null) {
      return null;
    }
    Class<?> sourceClass = value.getClass();
    if (targetClass.isAssignableFrom(sourceClass)) {
      return (T) value;
    }
    Converter<S, T> converter = resolveConverter(sourceClass, targetClass);
    if (converter != null) {
      return converter.apply((S) value, hints);
    } else {
      Converter<String, T> stringConverter = resolveConverter(String.class, targetClass);
      if (stringConverter != null) {
        LOGGER.fine(() -> String.format(
            "Can not find proper convert for %s -> %s, use String -> %s converter!", sourceClass,
            targetClass, targetClass));
        return stringConverter.apply(value.toString(), hints);
      }
    }
    throw new ConversionException("Can not find converter for type pair s% -> %s.", sourceClass,
        targetClass);
  }

  /**
   * Convert the given object to the target type object, use the given type literal.
   * <p>
   * Note: If the target type is generic, only List/Set/Supplier/Optional/Map, etc. are supported,
   * and the type parameter must be a concrete type.
   *
   * @param <T> the target type, including all actual type parameters
   * @param value the source object
   * @param typeLiteral the type literal
   */
  public static <T> T convert(Object value, TypeLiteral<T> typeLiteral) {
    return forceCast(convertType(value, typeLiteral.getType(), null));
  }

  /**
   * Convert the given object to the target type object, use the given type literal and hints. *
   * <p>
   * Note: If the target type is generic, only List/Set/Supplier/Optional/Map, etc. are supported,
   * and the type parameter must be a concrete type.
   *
   * @param <T> the target type, including all actual type parameters
   * @param value the source object
   * @param typeLiteral the type literal
   * @param hints the conversion hints
   */
  public static <T> T convert(Object value, TypeLiteral<T> typeLiteral, Map<String, ?> hints) {
    return forceCast(convertType(value, typeLiteral.getType(), hints));
  }

  /**
   * Convert an array value to target array
   *
   * @param <T> the target class of element of the array
   * @param value the array value to convert
   * @param targetItemClass the target element class of array
   * @param arrayFactory the constructor of array
   * @param hints the converter hints use for intervening converters
   * @return An array that elements were converted according to the target type
   */
  public static <T> T[] convert(Object[] value, Class<T> targetItemClass,
      IntFunction<T[]> arrayFactory, Map<String, ?> hints) {
    if (value == null) {
      return arrayFactory.apply(0);
    }
    T[] array = arrayFactory.apply(value.length);
    int i = 0;
    Iterator<T> it = convert(iterableOf(value), targetItemClass, hints).iterator();
    while (it.hasNext()) {
      array[i] = it.next();
      i++;
    }
    return array;
  }

  /**
   * Convert array value to target collection value
   *
   * @param <T> the target class of item of the collection
   * @param <C> the target collection class
   * @param value the array value to convert
   * @param collectionFactory the constructor of collection
   * @param targetItemClass the target class of item of the collection
   * @param hints the converter hints use for intervening converters
   * @return A collection of items converted according to the target type
   */
  public static <T, C extends Collection<T>> C convert(Object[] value,
      IntFunction<C> collectionFactory, Class<T> targetItemClass, Map<String, ?> hints) {
    if (value == null) {
      return null;
    }
    C collection = collectionFactory.apply(value.length);
    Iterator<T> it = convert(iterableOf(value), targetItemClass, hints).iterator();
    while (it.hasNext()) {
      collection.add(it.next());
    }
    return collection;
  }

  /**
   * Convert an object to target class object array with hints
   *
   * @param <S> the source value object class
   * @param <T> the target class
   * @param value the value to convert
   * @param targetClass the target class that convert to
   * @param hints the converter hints use for intervening converters
   * @return the converted object
   */
  public static <T> T[] convertArray(Object obj, Class<T> elementClazz, Map<String, ?> hints) {
    if (obj == null) {
      return (T[]) Array.newInstance(elementClazz, 0);
    } else if (obj instanceof Object[]) {
      Object[] arrayObj = (Object[]) obj;
      int length = arrayObj.length;
      Object array = Array.newInstance(elementClazz, length);
      for (int i = 0; i < length; i++) {
        Array.set(array, i, convert(arrayObj[i], elementClazz, hints));
      }
      return forceCast(array);
    } else if (obj.getClass().isArray()) {
      int length = Array.getLength(obj);
      Object array = Array.newInstance(elementClazz, length);
      for (int i = 0; i < length; i++) {
        Array.set(array, i, convert(Array.get(obj, i), elementClazz, hints));
      }
      return forceCast(array);
    } else if (obj instanceof Collection) {
      Collection<?> collObj = (Collection<?>) obj;
      int length = collObj.size();
      Object array = Array.newInstance(elementClazz, length);
      int i = 0;
      for (Object e : collObj) {
        Array.set(array, i, convert(e, elementClazz, hints));
        i++;
      }
      return forceCast(array);
    }
    throw new NotSupportedException("Only support Collection and Array as source");
  }

  /**
   * Convert an object to {@link Map}
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param value the source object
   * @param keyClass the key class
   * @param valueClass the value class
   * @param hints the conversion hints
   */
  public static <K, V> Map<K, V> convertMap(Object value, Class<K> keyClass, Class<V> valueClass,
      Map<String, ?> hints) {
    if (value == null) {
      return null;
    } else if (value instanceof Map) {
      Map vm = (Map) value;
      Map<K, V> map;
      if (value instanceof LinkedHashMap) {
        map = new LinkedHashMap<>(vm.size());
      } else if (value instanceof TreeMap) {
        map = new TreeMap<>();
      } else {
        map = new HashMap<>(vm.size());
      }
      vm.forEach((k, v) -> map.put(convert(k, keyClass, hints), convert(v, valueClass, hints)));
      return map;
    } else if (value instanceof Iterable) {
      Map<K, V> map = value instanceof List ? new LinkedHashMap<>() : new HashMap<>();
      Iterator<?> it = ((Iterable<?>) value).iterator();
      while (it.hasNext()) {
        K key = convert(it.next(), keyClass, hints);
        V val = null;
        if (it.hasNext()) {
          val = convert(it.next(), valueClass, hints);
        }
        map.put(key, val);
      }
      return map;
    } else if (value.getClass().isArray()) {
      int oLen = Array.getLength(value);
      int rLen = (oLen & 1) == 0 ? oLen : oLen - 1;
      int size = (rLen >> 1) + 1;
      Map<K, V> map = new LinkedHashMap<>(size);
      for (int i = 0; i < rLen; i += 2) {
        map.put(convert(Array.get(value, i), keyClass, hints),
            convert(Array.get(value, i + 1), valueClass, hints));
      }
      if (rLen < oLen) {
        map.put(convert(Array.get(value, rLen), keyClass, hints), null);
      }
      return map;
    }
    throw new IllegalArgumentException("Cannot type for Map<" + keyClass + "," + valueClass + ">");
  }

  /**
   * Convert the given object to the given target type. Note: If the target type is generic, only
   * List/Set/Supplier/Optional/Map, etc. are supported, and the type parameter must be a concrete
   * type.
   *
   * @param value the source object
   * @param targetType the target type
   * @param hints the conversion hints
   */
  static Object convertType(Object value, Type targetType, Map<String, ?> hints) {
    Object result = null;
    if (value != null) {
      final Class<?> typeClass;
      final Class[] argClasses;
      if (targetType instanceof Class) {
        typeClass = forceCast(targetType);
        argClasses = Classes.EMPTY_ARRAY;
      } else if (targetType instanceof ParameterizedType) {
        typeClass = forceCast(((ParameterizedType) targetType).getRawType());
        Type[] argTypes = ((ParameterizedType) targetType).getActualTypeArguments();
        argClasses = new Class[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
          if (!(argTypes[i] instanceof Class)) {
            throw new IllegalArgumentException("Cannot convert for type" + targetType
                + ", the parameterized type must be a concrete type");
          }
          argClasses[i] = (Class<?>) argTypes[i];
        }
      } else {
        throw new IllegalArgumentException("Cannot convert for type" + targetType);
      }

      try {
        if (typeClass.isArray()) {
          if (argClasses.length == 0) {
            result = convertArray(value, typeClass.getComponentType(), hints);
          } else {
            throw new IllegalArgumentException(
                "Cannot convert for type " + typeClass + "<" + Arrays.toString(argClasses) + "[]>");
          }
        } else {
          if (List.class.isAssignableFrom(typeClass) && argClasses.length == 1) {
            result = convert(value, argClasses[0], ArrayList::new, hints);
          } else if (Set.class.isAssignableFrom(typeClass) && argClasses.length == 1) {
            result = convert(value, argClasses[0], HashSet::new, hints);
          } else if (Optional.class.isAssignableFrom(typeClass) && argClasses.length == 1) {
            result = Optional.ofNullable(convert(value, argClasses[0]));
          } else if (Supplier.class.isAssignableFrom(typeClass) && argClasses.length == 1) {
            result = (Supplier<?>) () -> convert(value, argClasses[0]);
          } else if (Map.class.isAssignableFrom(typeClass) && argClasses.length == 2) {
            result = convertMap(value, argClasses[0], argClasses[1], hints);
          } else {
            if (argClasses.length == 0) {
              result = convert(value, typeClass, hints);
            } else {
              throw new IllegalArgumentException(
                  "Cannot convert for type " + typeClass + "<" + Arrays.toString(argClasses) + ">");
            }
          }
        }
      } catch (IllegalArgumentException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(
            String.format("Cannot convert value %s with type %s.", value, targetType), e);
      }
    }
    return result;
  }

  /**
   * Use source class and target class to find out the right converter
   *
   * @param sourceClass the source object class
   * @param targetClass the specified converted type
   */
  private static Converter resolveConverter(Class<?> sourceClass, Class<?> targetClass) {
    return Converters.lookup(wrap(sourceClass), wrap(targetClass)).orElse(null);
  }
}
