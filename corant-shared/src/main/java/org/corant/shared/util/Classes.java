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
package org.corant.shared.util;

import static org.corant.shared.util.Empties.isEmpty;
import static org.corant.shared.util.Functions.trySupplied;
import static org.corant.shared.util.Strings.isBlank;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.corant.shared.exception.CorantRuntimeException;

/**
 * corant-shared
 *
 * The class utility class.
 *
 * @author bingo 下午10:31:37
 *
 */
public class Classes {

  public static final String CGLIB_CLASS_SEPARATOR = "$$";
  public static final char PACKAGE_SEPARATOR_CHAR = '.';
  public static final String PACKAGE_SEPARATOR = String.valueOf(PACKAGE_SEPARATOR_CHAR);
  public static final char INNER_CLASS_SEPARATOR_CHAR = '$';
  public static final String INNER_CLASS_SEPARATOR = String.valueOf(INNER_CLASS_SEPARATOR_CHAR);
  public static final String CLASS_FILE_NAME_EXTENSION = ".class";

  private Classes() {
    super();
  }

  /**
   * Convert the class name string to Class using {@code classLoader}
   *
   * @param classLoader
   * @param className
   * @return
   * @throws ClassNotFoundException asClass
   */
  public static Class<?> asClass(final ClassLoader classLoader, final String className)
      throws ClassNotFoundException {
    return asClass(classLoader, className, true);
  }

  /**
   * Convert the class name string to Class using {@code classLoader}
   *
   * @param classLoader
   * @param className
   * @param initialize whether the class must be initialized
   * @return
   * @throws ClassNotFoundException asClass
   */
  public static Class<?> asClass(final ClassLoader classLoader, final String className,
      final boolean initialize) throws ClassNotFoundException {
    return Primitives.NAME_PRIMITIVE_MAP.getOrDefault(className,
        Class.forName(className, initialize, classLoader));
  }

  /**
   * Return Class from string class name.
   *
   * @param className
   * @return
   * @see #defaultClassLoader()
   */
  public static Class<?> asClass(String className) {
    try {
      return asClass(defaultClassLoader(), className, true);
    } catch (ClassNotFoundException e) {
      throw new CorantRuntimeException(e);
    }
  }

  public static void checkPackageAccess(Class<?> clazz) {
    checkPackageAccess(clazz.getName());
    if (Proxy.isProxyClass(clazz)) {
      checkProxyPackageAccess(clazz);
    }
  }

  public static void checkPackageAccess(String name) {
    SecurityManager s = System.getSecurityManager();
    if (s != null) {
      String cname = name.replace('/', '.');
      if (cname.startsWith("[")) {
        int b = cname.lastIndexOf('[') + 2;
        if (b > 1 && b < cname.length()) {
          cname = cname.substring(b);
        }
      }
      int i = cname.lastIndexOf('.');
      if (i != -1) {
        s.checkPackageAccess(cname.substring(0, i));
      }
    }
  }

  public static void checkProxyPackageAccess(Class<?> clazz) {
    SecurityManager s = System.getSecurityManager();
    if (s != null && Proxy.isProxyClass(clazz)) {
      // check proxy interfaces if the given class is a proxy class
      for (Class<?> intf : clazz.getInterfaces()) {
        checkPackageAccess(intf);
      }
    }
  }

  /**
   * Return the appropriate class loader. if the current thread has context class loader then return
   * it else return the Classes class loader, finally if class loader not found then return the
   * system class loader.
   *
   * @return defaultClassLoader
   */
  public static ClassLoader defaultClassLoader() {
    ClassLoader classLoader = trySupplied(Thread.currentThread()::getContextClassLoader);
    if (classLoader == null) {
      classLoader = Classes.class.getClassLoader();
      if (classLoader == null) {
        classLoader = trySupplied(ClassLoader::getSystemClassLoader);
      }
    }
    return classLoader;
  }

  /**
   * Return all the interfaces implemented by this {@code clazz}
   *
   * @param clazz
   * @return getAllInterfaces
   */
  public static List<Class<?>> getAllInterfaces(final Class<?> clazz) {
    Set<Class<?>> interfaces = new LinkedHashSet<>();
    if (clazz != null) {
      Class<?> usedClazz = clazz.isArray() ? clazz.getComponentType() : clazz;
      if (usedClazz.isInterface()) {
        interfaces.add(usedClazz);
      }
      Class<?> current = usedClazz;
      while (current != null) {
        Class<?>[] ifcs = current.getInterfaces();
        for (Class<?> ifc : ifcs) {
          interfaces.addAll(getAllInterfaces(ifc));
        }
        current = current.getSuperclass();
      }
    }
    return new ArrayList<>(interfaces);
  }

  /**
   * Return all the interfaces represented by this {@code object}
   *
   * @param object
   * @return getAllInterfaces
   */
  public static List<Class<?>> getAllInterfaces(final Object object) {
    return object == null ? new ArrayList<>() : getAllInterfaces(object.getClass());
  }

  /**
   * Return all the super classes extended by this {@code clazz}
   *
   * @param clazz
   * @return getAllSuperClasses
   */
  public static List<Class<?>> getAllSuperClasses(final Class<?> clazz) {
    List<Class<?>> superClasses = new ArrayList<>();
    if (clazz != null) {
      Class<?> usedClazz = clazz.isArray() ? clazz.getComponentType() : clazz;
      if (!usedClazz.isInterface() && usedClazz != Object.class) {
        Class<?> current = usedClazz;
        while (current.getSuperclass() != null) {
          superClasses.add(current.getSuperclass());
          current = current.getSuperclass();
        }
      }
    }
    return superClasses;
  }

  /**
   * Return all the super classes represented by this {@code object}
   *
   * @param object
   * @return getAllSuperClasses
   */
  public static List<Class<?>> getAllSuperClasses(final Object object) {
    return object == null ? new ArrayList<>() : getAllSuperClasses(object.getClass());
  }

  public static List<Class<?>> getAllSuperclassesAndInterfaces(final Class<?> clazz) {
    List<Class<?>> list = new ArrayList<>();
    list.addAll(getAllInterfaces(clazz));
    list.addAll(getAllSuperClasses(clazz));
    return list;
  }

  /**
   * stupid method :) need to reconstruct
   *
   * TODO FIXME
   *
   * Get component class from Iterable/Array/Iterator/Enumeration, return Object.class if not
   * resolved
   *
   * @param object
   * @return getComponentClass
   */
  public static Class<?> getComponentClass(Object object) {
    Class<?> clazz = null;
    if (object instanceof Iterable<?>) {
      for (Object cmp : (Iterable<?>) object) {
        if (cmp != null) {
          clazz = cmp.getClass();
          break;
        }
      }
    } else if (object != null && object.getClass().isArray()) {
      if (Array.getLength(object) == 0) {
        clazz = object.getClass().getComponentType();
      } else {
        for (Object obj : (Object[]) object) {
          if (obj != null) {
            clazz = obj.getClass();
            break;
          }
        }
      }
    } else if (object instanceof Iterator<?>) {
      Iterator<?> it = (Iterator<?>) object;
      while (clazz == null && it.hasNext()) {
        Object cmp = it.next();
        if (cmp != null) {
          clazz = cmp.getClass();
        }
      }
    } else if (object instanceof Enumeration<?>) {
      Enumeration<?> enums = (Enumeration<?>) object;
      while (clazz == null && enums.hasMoreElements()) {
        Object cmp = enums.nextElement();
        if (cmp != null) {
          clazz = cmp.getClass();
        }
      }
    } else if (object != null) {
      clazz = object.getClass();
    }
    if (clazz == null) {
      clazz = Object.class;
    }
    return getUserClass(clazz);
  }

  public static String getPackageName(String fullClassName) {
    if (isEmpty(fullClassName)) {
      return Strings.EMPTY;
    }
    int lastDot = fullClassName.lastIndexOf('.');
    return lastDot < 0 ? "" : fullClassName.substring(0, lastDot);
  }

  public static String getShortClassName(String className) {
    String shortClassName = className;
    if (isEmpty(shortClassName)) {
      return Strings.EMPTY;
    }
    final StringBuilder arrayPrefix = new StringBuilder();
    if (shortClassName.startsWith("[")) {
      while (shortClassName.charAt(0) == '[') {
        shortClassName = shortClassName.substring(1);
        arrayPrefix.append("[]");
      }
      if (shortClassName.charAt(0) == 'L'
          && shortClassName.charAt(shortClassName.length() - 1) == ';') {
        shortClassName = shortClassName.substring(1, shortClassName.length() - 1);
      }
    }
    final int lastDotIdx = shortClassName.lastIndexOf(PACKAGE_SEPARATOR_CHAR);
    final int innerIdx =
        shortClassName.indexOf(INNER_CLASS_SEPARATOR_CHAR, lastDotIdx == -1 ? 0 : lastDotIdx + 1);
    String out = shortClassName.substring(lastDotIdx + 1);
    if (innerIdx != -1) {
      out = out.replace(INNER_CLASS_SEPARATOR_CHAR, PACKAGE_SEPARATOR_CHAR);
    }
    return out + arrayPrefix;
  }

  public static Class<?> getUserClass(Class<?> clazz) {
    if (clazz != null && clazz.getName().contains(CGLIB_CLASS_SEPARATOR)) {
      Class<?> superclass = clazz.getSuperclass();
      if (superclass != null && Object.class != superclass) {
        return superclass;
      }
    }
    return clazz;
  }

  public static Class<?> getUserClass(Object instance) {
    return getUserClass(instance.getClass());
  }

  public static boolean isConcrete(Class<?> clazz) {
    return clazz != null && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
  }

  public static boolean isEnum(Class<?> clazz) {
    return clazz != null
        && (clazz.isEnum() || clazz.isArray() && clazz.getComponentType().isEnum());
  }

  public static void traverseAllInterfaces(final Class<?> clazz,
      Function<Class<?>, Boolean> visitor) {
    if (clazz != null) {
      Class<?> usedClazz = clazz.isArray() ? clazz.getComponentType() : clazz;
      if (usedClazz.isInterface() && !visitor.apply(usedClazz)) {
        return;
      }
      Class<?> current = usedClazz;
      stop: while (current != null) {
        Class<?>[] ifcs = current.getInterfaces();
        for (Class<?> ifc : ifcs) {
          for (Class<?> cls : getAllInterfaces(ifc)) {
            if (!visitor.apply(cls)) {
              break stop;
            }
          }
        }
        current = current.getSuperclass();
      }
    }
  }

  public static void traverseAllSuperClasses(final Class<?> clazz,
      Function<Class<?>, Boolean> visitor) {
    if (clazz != null) {
      Class<?> usedClazz = clazz.isArray() ? clazz.getComponentType() : clazz;
      if (!usedClazz.isInterface() && usedClazz != Object.class) {
        Class<?> current = usedClazz;
        while (current.getSuperclass() != null) {
          if (!visitor.apply(current.getSuperclass())) {
            break;
          }
          current = current.getSuperclass();
        }
      }
    }
  }

  public static Class<?> tryAsClass(String className) {
    if (isBlank(className)) {
      return null;
    }
    try {
      return asClass(defaultClassLoader(), className, true);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  static boolean isAssignable(Class<?> cls, final Class<?> toClass, final boolean autoboxing) {
    Class<?> useCls = cls;
    if (toClass == null) {
      return false;
    }
    if (useCls == null) {
      return !toClass.isPrimitive();
    }
    if (autoboxing) {
      if (useCls.isPrimitive() && !toClass.isPrimitive()) {
        useCls = Primitives.wrap(useCls);
        if (useCls == null) {
          return false;
        }
      }
      if (toClass.isPrimitive() && !useCls.isPrimitive()) {
        useCls = Primitives.unwrap(useCls);
        if (useCls == null) {
          return false;
        }
      }
    }
    if (useCls.equals(toClass)) {
      return true;
    }
    if (useCls.isPrimitive()) {
      if (!toClass.isPrimitive()) {
        return false;
      }
      if (Integer.TYPE.equals(useCls)) {
        return Long.TYPE.equals(toClass) || Float.TYPE.equals(toClass)
            || Double.TYPE.equals(toClass);
      }
      if (Long.TYPE.equals(useCls)) {
        return Float.TYPE.equals(toClass) || Double.TYPE.equals(toClass);
      }
      if (Boolean.TYPE.equals(useCls)) {
        return false;
      }
      if (Double.TYPE.equals(useCls)) {
        return false;
      }
      if (Float.TYPE.equals(useCls)) {
        return Double.TYPE.equals(toClass);
      }
      if (Character.TYPE.equals(useCls)) {
        return Integer.TYPE.equals(toClass) || Long.TYPE.equals(toClass)
            || Float.TYPE.equals(toClass) || Double.TYPE.equals(toClass);
      }
      if (Short.TYPE.equals(useCls)) {
        return Integer.TYPE.equals(toClass) || Long.TYPE.equals(toClass)
            || Float.TYPE.equals(toClass) || Double.TYPE.equals(toClass);
      }
      if (Byte.TYPE.equals(useCls)) {
        return Short.TYPE.equals(toClass) || Integer.TYPE.equals(toClass)
            || Long.TYPE.equals(toClass) || Float.TYPE.equals(toClass)
            || Double.TYPE.equals(toClass);
      }
      // should never get here
      return false;
    }
    return toClass.isAssignableFrom(useCls);
  }

}
