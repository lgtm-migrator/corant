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
package org.corant.suites.jpa.shared;

import static org.corant.shared.util.Assertions.shouldBeFalse;
import static org.corant.shared.util.Assertions.shouldBeTrue;
import static org.corant.shared.util.ObjectUtils.isEquals;
import static org.corant.shared.util.StringUtils.defaultTrim;
import static org.corant.shared.util.StringUtils.isBlank;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.inject.Named;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceUnit;
import org.corant.Corant;
import org.corant.kernel.util.Cdis;
import org.corant.suites.jpa.shared.inject.EntityManagerBean;
import org.corant.suites.jpa.shared.inject.EntityManagerFactoryBean;
import org.corant.suites.jpa.shared.inject.ExtendedPersistenceContextType;
import org.corant.suites.jpa.shared.inject.JpaProvider;
import org.corant.suites.jpa.shared.inject.JpaProvider.JpaProviderLiteral;
import org.corant.suites.jpa.shared.inject.PersistenceContextInjectionPoint;
import org.corant.suites.jpa.shared.inject.TransactionPersistenceContextType;
import org.corant.suites.jpa.shared.metadata.PersistenceContextMetaData;
import org.corant.suites.jpa.shared.metadata.PersistenceUnitInfoMetaData;
import org.corant.suites.jpa.shared.metadata.PersistenceUnitMetaData;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * corant-suites-jpa-shared
 *
 * TODO Support unnamed persistence unit
 *
 * @author bingo 上午11:32:56
 *
 */
public class JpaExtension implements Extension {

  protected final Set<PersistenceUnitMetaData> persistenceUnits =
      Collections.newSetFromMap(new ConcurrentHashMap<PersistenceUnitMetaData, Boolean>());
  protected final Set<PersistenceContextMetaData> persistenceContexts =
      Collections.newSetFromMap(new ConcurrentHashMap<PersistenceContextMetaData, Boolean>());
  protected final Map<String, PersistenceUnitInfoMetaData> persistenceUnitInfoMetaDatas =
      new ConcurrentHashMap<>();
  protected final Map<Named, EntityManagerFactory> entityManagerFactories =
      new ConcurrentHashMap<>();
  protected Logger logger = Logger.getLogger(getClass().getName());

  public Map<Named, EntityManagerFactory> getEntityManagerFactories() {
    return Collections.unmodifiableMap(entityManagerFactories);
  }

  /**
   * This method can only be called after initialization
   *
   * @param unitName
   * @return getEntityManagerFactory
   */
  public EntityManagerFactory getEntityManagerFactory(Named unitName) {
    return entityManagerFactories.computeIfAbsent(unitName, (un) -> {
      PersistenceUnitInfoMetaData puim = getPersistenceUnitInfoMetaData(unitName.value());
      String providerName = puim.getPersistenceProviderClassName();
      JpaProvider jp = JpaProviderLiteral.of(providerName);
      Instance<AbstractJpaProvider> provider =
          Corant.instance().select(AbstractJpaProvider.class, jp);
      shouldBeTrue(provider.isResolvable(), "Can not find jpa provider named %s.", jp.value());
      final EntityManagerFactory emf = provider.get().buildEntityManagerFactory(puim);
      return emf;
    });
  }

  public EntityManagerFactory getEntityManagerFactory(String unitName) {
    return getEntityManagerFactory(resolvePersistenceUnitQualifier(unitName));
  }

  public PersistenceUnitInfoMetaData getPersistenceUnitInfoMetaData(String name) {
    return persistenceUnitInfoMetaDatas.get(defaultTrim(name));
  }

  public Named resolvePersistenceUnitQualifier(String name) {
    return isBlank(name) ? NamedLiteral.INSTANCE : NamedLiteral.of(name);
  }

  void onAfterBeanDiscovery(@Observes final AfterBeanDiscovery abd, final BeanManager beanManager) {
    // assembly
    persistenceContexts.forEach(pc -> {
      if (persistenceUnits.stream().map(PersistenceUnitMetaData::getUnitName)
          .noneMatch((un) -> isEquals(pc.getUnitName(), un))) {
        persistenceUnits.add(new PersistenceUnitMetaData(null, pc.getUnitName()));
      }
    });
    Map<String, PersistenceUnitInfoMetaData> copy = new HashMap<>(persistenceUnitInfoMetaDatas);
    // some injections
    persistenceUnits.forEach(pumd -> {
      final String unitName = pumd.getUnitName();
      copy.remove(unitName);
      abd.addBean(new EntityManagerFactoryBean(beanManager, unitName));
    });
    persistenceContexts.forEach(pcmd -> {
      copy.remove(pcmd.getUnitName());
      Annotation qualifier = resolvePersistenceUnitQualifier(pcmd.getUnitName());
      abd.addBean(new EntityManagerBean(beanManager, pcmd, qualifier));
    });
    // not injection but has been configurated
    for (Iterator<Entry<String, PersistenceUnitInfoMetaData>> it = copy.entrySet().iterator(); it
        .hasNext();) {
      Entry<String, PersistenceUnitInfoMetaData> entry = it.next();
      abd.addBean(new EntityManagerFactoryBean(beanManager, entry.getKey()));
      it.remove();
    }
  }

  void onBeforeBeanDiscovery(@Observes final BeforeBeanDiscovery event) {
    JpaConfig.from(ConfigProvider.getConfig()).forEach(persistenceUnitInfoMetaDatas::put);
  }

  void onProcessInjectionPoint(@Observes ProcessInjectionPoint<?, ?> pip, BeanManager beanManager) {
    final InjectionPoint ip = pip.getInjectionPoint();
    final PersistenceUnit pu = Cdis.getAnnotated(ip).getAnnotation(PersistenceUnit.class);
    if (pu != null) {
      persistenceUnits.add(new PersistenceUnitMetaData(pu));
      pip.configureInjectionPoint().addQualifier(resolvePersistenceUnitQualifier(pu.unitName()));
    }
    final PersistenceContext pc = Cdis.getAnnotated(ip).getAnnotation(PersistenceContext.class);
    if (pc != null) {
      if (pc.type() != PersistenceContextType.TRANSACTION) {
        shouldBeFalse(ip.getBean().getScope().equals(ApplicationScoped.class));
        pip.setInjectionPoint(new PersistenceContextInjectionPoint(ip,
            ExtendedPersistenceContextType.INST, Any.Literal.INSTANCE));
      } else {
        pip.setInjectionPoint(new PersistenceContextInjectionPoint(ip,
            TransactionPersistenceContextType.INST, Any.Literal.INSTANCE));
      }
      persistenceContexts.add(new PersistenceContextMetaData(pc));
    }
  }
}
