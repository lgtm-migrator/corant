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
package org.corant.suites.jpa.shared.inject;

import static org.corant.shared.util.Assertions.shouldBeTrue;
import static org.corant.shared.util.Assertions.shouldNotNull;
import static org.corant.shared.util.CollectionUtils.asSet;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;
import javax.persistence.EntityManagerFactory;
import org.corant.Corant;
import org.corant.suites.jpa.shared.JpaExtension;

/**
 * corant-suites-jpa-shared
 *
 * @author bingo 上午10:34:41
 *
 */
public class EntityManagerFactoryBean implements Bean<EntityManagerFactory>, PassivationCapable {

  static final Logger logger = Logger.getLogger(EntityManagerFactoryBean.class.getName());
  static final Set<Type> types = Collections.unmodifiableSet(asSet(EntityManagerFactory.class));
  final Set<Annotation> qualifiers = new HashSet<>();
  final BeanManager beanManager;
  final String unitName;

  /**
   * @param beanManager
   * @param unitName
   * @param qualifiers
   */
  public EntityManagerFactoryBean(BeanManager beanManager, String unitName) {
    this.beanManager = beanManager;
    this.unitName = shouldNotNull(unitName);
    qualifiers.add(Any.Literal.INSTANCE);
    qualifiers.add(NamedLiteral.of(unitName));
  }

  @Override
  public EntityManagerFactory create(CreationalContext<EntityManagerFactory> creationalContext) {
    Instance<JpaExtension> ext = Corant.instance().select(JpaExtension.class);
    shouldBeTrue(ext.isResolvable(), "Can not find jpa extension.");
    return ext.get().getEntityManagerFactory(unitName);
  }

  @Override
  public void destroy(EntityManagerFactory instance,
      CreationalContext<EntityManagerFactory> creationalContext) {
    if (instance != null && instance.isOpen()) {
      instance.close();
      logger.info(() -> String
          .format("Destroyed entity manager factory that persistence unit named %s.", unitName));
    }
  }

  @Override
  public Class<?> getBeanClass() {
    return EntityManagerFactoryBean.class;
  }

  @Override
  public String getId() {
    return EntityManagerFactoryBean.class.getName() + "." + unitName;
  }

  @Override
  public Set<InjectionPoint> getInjectionPoints() {
    return Collections.emptySet();
  }

  @Override
  public String getName() {
    return "EntityManagerFactoryBean." + unitName;
  }

  @Override
  public Set<Annotation> getQualifiers() {
    return qualifiers;
  }

  @Override
  public Class<? extends Annotation> getScope() {
    return ApplicationScoped.class;
  }

  @Override
  public Set<Class<? extends Annotation>> getStereotypes() {
    return Collections.emptySet();
  }

  @Override
  public Set<Type> getTypes() {
    return types;
  }

  @Override
  public boolean isAlternative() {
    return false;
  }

  @Override
  public boolean isNullable() {
    return false;
  }

}
