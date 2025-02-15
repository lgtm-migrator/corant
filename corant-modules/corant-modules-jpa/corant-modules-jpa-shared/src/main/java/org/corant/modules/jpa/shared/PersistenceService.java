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
package org.corant.modules.jpa.shared;

import static org.corant.shared.util.Assertions.shouldNotNull;
import static org.corant.shared.util.Empties.isEmpty;
import static org.corant.shared.util.Maps.mapOf;
import static org.corant.shared.util.Objects.defaultObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.enterprise.util.AnnotationLiteral;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceProperty;
import javax.persistence.PersistenceUnit;
import javax.persistence.SynchronizationType;
import org.corant.context.qualifier.Qualifiers;

/**
 * corant-modules-jpa-shared
 *
 * @author bingo 下午2:08:53
 *
 */
public interface PersistenceService {

  String EMPTY_PERSISTENCE_UNIT_NAME = Qualifiers.EMPTY_NAME;

  /**
   * Returns the managed entity manager for given persistence context
   *
   * @param pc
   * @return getEntityManager
   */
  EntityManager getEntityManager(PersistenceContext pc);

  /**
   * Returns the managed entity manager for given persistence unit name, the implicit type of the
   * persistence context is TRANSACTION and synchronization of the persistence context is
   * SYNCHRONIZED
   *
   * @param persistenceUnitName the persistence unit name
   */
  default EntityManager getEntityManager(String persistenceUnitName) {
    return getEntityManager(PersistenceContextLiteral.of(persistenceUnitName));
  }

  /**
   * Returns the managed entity manager factory for given persistence context
   *
   * @param pc
   * @return getEntityManagerFactory
   */
  default EntityManagerFactory getEntityManagerFactory(PersistenceContext pc) {
    return getEntityManagerFactory(PersistenceUnitLiteral.of(pc));
  }

  /**
   * Returns the managed entity manager factory for given persistence unit
   *
   * @param pu
   * @return getEntityManagerFactory
   */
  EntityManagerFactory getEntityManagerFactory(PersistenceUnit pu);

  /**
   * Returns the managed entity manager factory for given persistence unit name
   *
   * @param persistenceUnitName
   * @return getEntityManagerFactory
   */
  default EntityManagerFactory getEntityManagerFactory(String persistenceUnitName) {
    return getEntityManagerFactory(PersistenceUnitLiteral.of(persistenceUnitName));
  }

  /**
   * corant-modules-jpa-shared
   *
   * @author bingo 下午12:01:07
   *
   */
  class PersistenceContextLiteral extends AnnotationLiteral<PersistenceContext>
      implements PersistenceContext {

    private static final long serialVersionUID = -6911793060874174440L;

    private final String name;
    private final String unitName;
    private final PersistenceContextType type;
    private final SynchronizationType synchronization;
    private final Map<String, String> properties;

    /**
     * @param name
     * @param unitName
     * @param type
     * @param synchronization
     * @param properties
     */
    protected PersistenceContextLiteral(String name, String unitName, PersistenceContextType type,
        SynchronizationType synchronization, Map<String, String> properties) {
      this.name = Qualifiers.resolveName(name);
      this.unitName = Qualifiers.resolveName(unitName);
      this.type = defaultObject(type, PersistenceContextType.TRANSACTION);
      this.synchronization = defaultObject(synchronization, SynchronizationType.SYNCHRONIZED);
      if (!isEmpty(properties)) {
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
      } else {
        this.properties = Collections.emptyMap();
      }
    }

    public static Map<String, String> extractProperties(PersistenceProperty[] pps) {
      Map<String, String> map = new LinkedHashMap<>();
      if (!isEmpty(pps)) {
        for (PersistenceProperty pp : pps) {
          map.put(pp.name(), pp.value());
        }
      }
      return map;
    }

    public static PersistenceContextLiteral of(PersistenceContext pc) {
      shouldNotNull(pc);
      return new PersistenceContextLiteral(pc.name(), pc.unitName(), pc.type(),
          pc.synchronization(), extractProperties(pc.properties()));
    }

    public static PersistenceContextLiteral of(String unitName) {
      return of(unitName, null, null, (String[]) null);
    }

    public static PersistenceContextLiteral of(String unitName, PersistenceContextType type) {
      return of(unitName, type, null, (String[]) null);
    }

    public static PersistenceContextLiteral of(String unitName, PersistenceContextType type,
        SynchronizationType synchronization) {
      return of(unitName, type, synchronization, (String[]) null);
    }

    public static PersistenceContextLiteral of(String unitName, PersistenceContextType type,
        SynchronizationType synchronization, String... properties) {
      return new PersistenceContextLiteral(null, unitName, type, synchronization,
          mapOf((Object[]) properties));
    }

    public static PersistenceContextLiteral of(String unitName,
        SynchronizationType synchronization) {
      return of(unitName, null, synchronization, (String[]) null);
    }

    public Map<String, String> getProperties() {
      return properties;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public PersistenceProperty[] properties() {
      return getProperties().entrySet().stream()
          .map(e -> new PersistencePropertyLiteral(e.getKey(), e.getValue()))
          .toArray(PersistenceProperty[]::new);
    }

    @Override
    public SynchronizationType synchronization() {
      return synchronization;
    }

    @Override
    public PersistenceContextType type() {
      return type;
    }

    @Override
    public String unitName() {
      return unitName;
    }
  }

  /**
   * corant-modules-jpa-shared
   *
   * @author bingo 上午11:50:24
   *
   */
  class PersistencePropertyLiteral extends AnnotationLiteral<PersistenceProperty>
      implements PersistenceProperty {
    private static final long serialVersionUID = -5166046527595649735L;

    final String name;
    final String value;

    public PersistencePropertyLiteral(String name, String value) {
      this.name = Qualifiers.resolveName(name);
      this.value = Qualifiers.resolveName(value);
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String value() {
      return value;
    }

  }

  /**
   * corant-modules-jpa-shared
   *
   * @author bingo 下午12:04:52
   *
   */
  class PersistenceUnitLiteral extends AnnotationLiteral<PersistenceUnit>
      implements PersistenceUnit {
    private static final long serialVersionUID = -2508891695595998643L;
    private final String name;
    private final String unitName;

    /**
     * @param name
     * @param unitName
     */
    protected PersistenceUnitLiteral(String name, String unitName) {
      this.name = Qualifiers.resolveName(name);
      this.unitName = Qualifiers.resolveName(unitName);
    }

    public static PersistenceUnitLiteral of(PersistenceContext pc) {
      shouldNotNull(pc);
      return new PersistenceUnitLiteral(pc.name(), pc.unitName());
    }

    public static PersistenceUnitLiteral of(PersistenceUnit pu) {
      shouldNotNull(pu);
      return new PersistenceUnitLiteral(pu.name(), pu.unitName());
    }

    public static PersistenceUnitLiteral of(String unitName) {
      return new PersistenceUnitLiteral(null, unitName);
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String unitName() {
      return unitName;
    }
  }
}
