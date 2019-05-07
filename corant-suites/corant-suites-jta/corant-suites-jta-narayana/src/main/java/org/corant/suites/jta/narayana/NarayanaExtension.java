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
package org.corant.suites.jta.narayana;

import static org.corant.Corant.instance;
import static org.corant.shared.util.ClassUtils.defaultClassLoader;
import static org.corant.shared.util.CollectionUtils.listOf;
import static org.corant.shared.util.Empties.isEmpty;
import static org.corant.shared.util.StreamUtils.streamOf;
import static org.corant.shared.util.StringUtils.split;
import java.util.Collection;
import java.util.Hashtable;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.UserTransaction;
import org.corant.kernel.config.ComparableConfigurator;
import org.corant.shared.exception.CorantRuntimeException;
import org.corant.shared.normal.Defaults;
import org.eclipse.microprofile.config.ConfigProvider;
import com.arjuna.ats.arjuna.common.CoordinatorEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.CheckedAction;
import com.arjuna.ats.arjuna.logging.tsLogger;
import com.arjuna.ats.jbossatx.jta.RecoveryManagerService;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.ats.jta.utils.JNDIManager;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

/**
 * corant-suites-jta-narayana
 *
 * @author bingo 下午7:19:18
 *
 */
public class NarayanaExtension implements Extension {

  protected final transient Logger logger = Logger.getLogger(this.getClass().toString());

  void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
    try {
      JNDIManager.bindJTAImplementation();
      logger.info(() -> "Bind JTA implementations to Jndi.");
    } catch (NamingException e) {
      throw new CorantRuntimeException(e,
          "An error occurred while registering Transaction Manager to JNDI");
    }

    if (event != null) {
      // The TransactionSynchronizationRegistry && TransactionManager have been registered by
      // narayana self see com.arjuna.ats.jta.cdi.TransactionExtension
      final Collection<? extends Bean<?>> userTransactionBeans =
          beanManager.getBeans(UserTransaction.class);
      if (isEmpty(userTransactionBeans)) {
        // For OpenWebBeans
        event.addBean().types(UserTransaction.class)
            .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE).scope(Dependent.class)
            .createWith(cc -> com.arjuna.ats.jta.UserTransaction.userTransaction());
      }

      event.addBean().id(Transaction.class.getName())
          .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE).types(Transaction.class)
          .scope(TransactionScoped.class).createWith(cc -> {
            try {
              return instance().select(TransactionManager.class).get().getTransaction();
            } catch (final SystemException systemException) {
              throw new CreationException(systemException.getMessage(), systemException);
            }
          });

      event.addBean().addTransitiveTypeClosure(JTAEnvironmentBean.class)
          .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE).scope(Singleton.class)
          .createWith(cc -> BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class));

      event.<RecoveryManagerService>addBean().addTransitiveTypeClosure(RecoveryManagerService.class)
          .addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE,
              NamedLiteral.of("narayana-jta"))
          .scope(Singleton.class).createWith(cc -> {
            RecoveryManagerService rms = new RecoveryManagerService();
            rms.create();
            return rms;
          }).disposeWith((t, inst) -> t.destroy());
    }
  }

  void beforeBeanDiscovery(@Observes final BeforeBeanDiscovery event,
      final BeanManager beanManager) {
    NarayanaConfig config = NarayanaConfig.of(ConfigProvider.getConfig());

    String dfltObjStoreDir =
        config.getObjectStoreDir().orElse(Defaults.corantUserDir("-narayana-objects").toString());
    final ObjectStoreEnvironmentBean nullActionStoreObjectStoreEnvironmentBean =
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, null);
    nullActionStoreObjectStoreEnvironmentBean.setObjectStoreDir(dfltObjStoreDir);
    final ObjectStoreEnvironmentBean defaultActionStoreObjectStoreEnvironmentBean =
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "default");
    defaultActionStoreObjectStoreEnvironmentBean.setObjectStoreDir(dfltObjStoreDir);
    final ObjectStoreEnvironmentBean stateStoreObjectStoreEnvironmentBean =
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "stateStore");
    stateStoreObjectStoreEnvironmentBean.setObjectStoreDir(dfltObjStoreDir);
    final ObjectStoreEnvironmentBean communicationStoreObjectStoreEnvironmentBean =
        BeanPopulator.getNamedInstance(ObjectStoreEnvironmentBean.class, "communicationStore");
    communicationStoreObjectStoreEnvironmentBean.setObjectStoreDir(dfltObjStoreDir);

    final CoordinatorEnvironmentBean coordinatorEnvironmentBean =
        BeanPopulator.getDefaultInstance(CoordinatorEnvironmentBean.class);
    coordinatorEnvironmentBean.setDefaultTimeout(config.getTransactionTimeout());
    interruptCheckedActionIfNecessary(coordinatorEnvironmentBean, config);

    if (Thread.currentThread().getContextClassLoader()
        .getResource("jbossts-properties.xml") != null) {
      logger.info(() -> "Use class path file jbossts-properties.xml as default setting.");
      return;
    }

    coordinatorEnvironmentBean.setCommitOnePhase(config.getCommitOnePhase());

    final CoreEnvironmentBean coreEnvironmentBean =
        BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
    config.getNodeIdentifier().ifPresent(t -> {
      try {
        coreEnvironmentBean.setNodeIdentifier(t);
      } catch (CoreEnvironmentBeanException e) {
        throw new CorantRuntimeException(e);
      }
    });
    coreEnvironmentBean.setSocketProcessIdPort(config.getSocketProcessIdPort());

    final JTAEnvironmentBean jtaEnvironmentBean =
        BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);
    config.getXaRecoveryNodes()
        .ifPresent(x -> jtaEnvironmentBean.setXaRecoveryNodes(listOf(split(x, ",", true, true))));
    config.getXaResourceOrphanFilterClassNames().ifPresent(x -> jtaEnvironmentBean
        .setXaResourceOrphanFilterClassNames(listOf(split(x, ",", true, true))));

    final RecoveryEnvironmentBean recoveryEnvironmentBean =
        BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class);
    recoveryEnvironmentBean.setPeriodicRecoveryPeriod(config.getPeriodicRecoveryPeriod());
    recoveryEnvironmentBean.setRecoveryBackoffPeriod(config.getRecoveryBackoffPeriod());
    config.getRecoveryModuleClassNames().ifPresent(x -> recoveryEnvironmentBean
        .setRecoveryModuleClassNames(listOf(split(x, ",", true, true))));
    config.getExpiryScannerClassNames().ifPresent(
        x -> recoveryEnvironmentBean.setExpiryScannerClassNames(listOf(split(x, ",", true, true))));

    config.getRecoveryPort().ifPresent(recoveryEnvironmentBean::setRecoveryPort);
    config.getRecoveryAddress().ifPresent(recoveryEnvironmentBean::setRecoveryAddress);
    config.getTransactionStatusManagerPort()
        .ifPresent(recoveryEnvironmentBean::setTransactionStatusManagerPort);
    config.getTransactionStatusManagerAddress()
        .ifPresent(recoveryEnvironmentBean::setTransactionStatusManagerAddress);
    config.getRecoveryListener().ifPresent(recoveryEnvironmentBean::setRecoveryListener);

    streamOf(ServiceLoader.load(NarayanaConfigurator.class, defaultClassLoader()))
        .sorted(ComparableConfigurator::compare).forEach(cfgr -> {
          logger.info(() -> String.format("Use customer narayana configurator $s.",
              cfgr.getClass().getName()));
          cfgr.configCoreEnvironment(coreEnvironmentBean);
          cfgr.configCoordinatorEnvironment(coordinatorEnvironmentBean);
          cfgr.configRecoveryEnvironment(recoveryEnvironmentBean);
          cfgr.configObjectStoreEnvironment(nullActionStoreObjectStoreEnvironmentBean, null);
          cfgr.configObjectStoreEnvironment(defaultActionStoreObjectStoreEnvironmentBean,
              "default");
          cfgr.configObjectStoreEnvironment(stateStoreObjectStoreEnvironmentBean, "stateStore");
          cfgr.configObjectStoreEnvironment(communicationStoreObjectStoreEnvironmentBean,
              "communicationStore");
        });
  }

  private void interruptCheckedActionIfNecessary(
      CoordinatorEnvironmentBean coordinatorEnvironmentBean, NarayanaConfig config) {
    if (config.getTransactionTimeout() != null && config.getTransactionTimeout() > 0) {
      logger.warning(
          () -> "Use thread interrupt checked action for narayana, It can cause inconsistencies.");
      coordinatorEnvironmentBean.setAllowCheckedActionFactoryOverride(true);
      coordinatorEnvironmentBean
          .setCheckedActionFactory((txId, actionType) -> new InterruptCheckedAction());
      return;
    }
  }

  public static class InterruptCheckedAction extends CheckedAction {

    protected final transient Logger logger = Logger.getLogger(this.getClass().toString());

    @Override
    public synchronized void check(boolean isCommit, Uid actUid,
        @SuppressWarnings("rawtypes") Hashtable list) {
      if (isCommit) {
        tsLogger.i18NLogger.warn_coordinator_CheckedAction_1(actUid, Integer.toString(list.size()));
      } else {
        try {
          for (Object item : list.values()) {
            if (item instanceof Thread) {
              Thread thread = Thread.class.cast(item);
              try {
                Throwable t =
                    new Throwable("STACK TRACE OF ACTIVE THREAD IN TERMINATING TRANSACTION");
                t.setStackTrace(thread.getStackTrace());
                logger.log(Level.INFO, t,
                    () -> String.format("Transaction %s is %s with active thread %s",
                        actUid.toString(), isCommit ? "committing" : "aborting", thread.getName()));
              } catch (Exception e) {
                logger.log(Level.WARNING, e,
                    () -> String.format(
                        "Narayana extension checked action execute failed on %s , isCommit %s .",
                        actUid, isCommit));
              }
              thread.interrupt();
            }
          }
        } catch (Exception e) {
          logger.log(Level.WARNING, e,
              () -> String.format(
                  "Narayana extension checked action execute failed on %s , isCommit %s .", actUid,
                  isCommit));
        }
        tsLogger.i18NLogger.warn_coordinator_CheckedAction_2(actUid, Integer.toString(list.size()));
      }
    }
  }
}
