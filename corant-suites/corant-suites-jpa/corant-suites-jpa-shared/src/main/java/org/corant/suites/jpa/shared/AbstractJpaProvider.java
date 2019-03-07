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

import static org.corant.shared.util.StringUtils.isNotBlank;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManagerFactory;
import org.corant.shared.exception.CorantRuntimeException;
import org.corant.suites.jpa.shared.metadata.PersistenceUnitInfoMetaData;

/**
 * corant-suites-jpa-shared
 *
 * @author bingo 上午11:08:47
 *
 */
@ApplicationScoped
public abstract class AbstractJpaProvider {

  protected volatile boolean initedJndiSubCtx = false;
  protected Logger logger = Logger.getLogger(getClass().getName());

  @Inject
  InitialContext jndi;

  public abstract EntityManagerFactory buildEntityManagerFactory(
      PersistenceUnitInfoMetaData metaData);

  protected EntityManagerFactory createEntityManagerFactory(PersistenceUnitInfoMetaData metaData) {
    final EntityManagerFactory emf = buildEntityManagerFactory(metaData);
    if (getJndi() != null && isNotBlank(metaData.getPersistenceUnitName())) {
      try {
        if (!initedJndiSubCtx) {
          getJndi().createSubcontext(JpaConfig.JNDI_SUBCTX_NAME);
          initedJndiSubCtx = true;
        }
        String jndiName = JpaConfig.JNDI_SUBCTX_NAME + "/" + metaData.getPersistenceUnitName();
        jndi.bind(jndiName, emf);
        logger.info(() -> String.format("Bind entity manager factory %s to jndi!", jndiName));
      } catch (NamingException e) {
        throw new CorantRuntimeException(e);
      }
    }
    return emf;
  }

  protected InitialContext getJndi() {
    return jndi;
  }
}
