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
package org.corant.suites.jms.shared.receive;

import static org.corant.shared.util.Assertions.shouldBeTrue;
import static org.corant.shared.util.Assertions.shouldNotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.inject.Inject;
import org.corant.kernel.event.PostCorantReadyEvent;
import org.corant.shared.util.ObjectUtils.Pair;
import org.corant.suites.jms.shared.AbstractJMSConfig;
import org.corant.suites.jms.shared.AbstractJMSExtension;

/**
 * corant-suites-jms-shared
 *
 * @author bingo 下午2:37:45
 *
 */
@ApplicationScoped
public class MessageReceiverManager {

  @Inject
  Logger logger;

  @Inject
  AbstractJMSExtension extesion;

  final Map<Object, ScheduledExecutorService> executorServices = new HashMap<>();

  final Set<MessageReceiverMetaData> receiveMetaDatas =
      Collections.newSetFromMap(new ConcurrentHashMap<MessageReceiverMetaData, Boolean>());

  protected MessageReceiverTask buildTask(MessageReceiverMetaData metaData) {
    return new MessageReceiverTask(metaData);
  }

  void beforeShutdown(@Observes final BeforeShutdown e) {
    logger.info(() -> "Shut down the message receiver executor services");
    executorServices.values().forEach(es -> es.shutdownNow().forEach(r -> {
      if (r instanceof MessageReceiverTask) {
        ((MessageReceiverTask) r).release(true);
      }
    }));
  }

  void onPostCorantReadyEvent(@Observes PostCorantReadyEvent adv) {
    Set<Pair<String, String>> anycasts = new HashSet<>();
    for (final MessageReceiverMetaData metaData : receiveMetaDatas) {
      if (!metaData.isMulticast()
          && !anycasts.add(Pair.of(metaData.getConnectionFactoryId(), metaData.getDestination()))) {
        logger.warning(() -> String.format(
            "The anycast message receiver with same connection factory id and same destination appeared more than once, it should be avoided in general. message receiver [%s].",
            metaData));
      }
      final AbstractJMSConfig cfg =
          AbstractJMSExtension.getConfig(metaData.getConnectionFactoryId());
      if (cfg != null && cfg.isEnable()) {
        if (metaData.xa()) {
          shouldBeTrue(cfg.isXa(),
              "Can not schedule xa message receiver task, the connection factory [%s] not supported! message receiver [%s].",
              cfg.getConnectionFactoryId(), metaData);
        }
        ScheduledExecutorService ses = shouldNotNull(
            executorServices.get(cfg.getConnectionFactoryId()),
            "Can not schedule message receiver task, connection factory id [%s] not found. message receiver [%s].",
            cfg.getConnectionFactoryId(), metaData);
        ses.scheduleWithFixedDelay(buildTask(metaData), cfg.getReceiveTaskInitialDelayMs(),
            cfg.getReceiveTaskDelayMs(), TimeUnit.MICROSECONDS);
        logger.fine(() -> String.format(
            "Scheduled message receiver task, connection factory id [%s], destination [%s], initial delay [%s]Ms",
            metaData.getConnectionFactoryId(), metaData.getDestination(),
            cfg.getReceiveTaskInitialDelayMs()));
      }
    }
    anycasts.clear();
  }

  @PostConstruct
  void postConstruct() {
    extesion.getReceiveMethods().stream().map(MessageReceiverMetaData::of)
        .forEach(receiveMetaDatas::addAll);
    if (!receiveMetaDatas.isEmpty()) {
      extesion.getConfigManager().getAllWithNames().values().forEach(cfg -> {
        if (cfg != null && cfg.isEnable()) {
          executorServices.put(cfg.getConnectionFactoryId(),
              Executors.newScheduledThreadPool(cfg.getReceiveTaskThreads()));
        }
      });
      logger.info(
          () -> String.format("Find %s message receivers that involving %s connection factories.",
              receiveMetaDatas.size(), executorServices.size()));
    }
  }

}
