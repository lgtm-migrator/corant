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
package org.corant.context.command;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import org.corant.context.qualifier.TypeArgument.TypeArgumentLiteral;
import org.corant.shared.normal.Priorities;
import org.corant.shared.ubiquity.Tuple.Pair;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * corant-context
 *
 * @author bingo 下午10:08:26
 *
 */
public class CommanderExtension implements Extension {

  private static final Logger logger = Logger.getLogger(CommanderExtension.class.getName());

  static final Map<Class<?>, Set<Class<?>>> commandAndHandler = new ConcurrentHashMap<>();

  static final boolean USE_COMMAND_PATTERN = ConfigProvider.getConfig()
      .getOptionalValue("corant.context.command.enable", Boolean.class).orElse(Boolean.TRUE);
  static final boolean SUPPORT_ABSTRACT_COMMAND = ConfigProvider.getConfig()
      .getOptionalValue("corant.context.command.support-abstract-command", Boolean.class)
      .orElse(Boolean.FALSE);

  void arrange(@Observes @Priority(Priorities.FRAMEWORK_HIGHER) @WithAnnotations({
      Commands.class}) ProcessAnnotatedType<?> event) {
    if (USE_COMMAND_PATTERN) {
      Class<?> handlerCls = event.getAnnotatedType().getJavaClass();
      if (!handlerCls.isInterface() && !Modifier.isAbstract(handlerCls.getModifiers())
          && CommandHandler.class.isAssignableFrom(handlerCls)) {
        Class<?> cmdCls = resolveCommandType(handlerCls);
        if (cmdCls == null) {
          logger.warning(() -> String
              .format("Can not find any command type parameter for handler [%s]", handlerCls));
        } else {
          if (!cmdCls.isInterface() && !Modifier.isAbstract(cmdCls.getModifiers())) {
            event.configureAnnotatedType().add(TypeArgumentLiteral.of(cmdCls));
            commandAndHandler.computeIfAbsent(cmdCls, k -> new HashSet<>()).add(handlerCls);
            logger.fine(() -> String.format("Resolved the command [%s] with handler [%s]", cmdCls,
                handlerCls));
          } else if (SUPPORT_ABSTRACT_COMMAND) {
            event.configureAnnotatedType().add(TypeArgumentLiteral.of(cmdCls));
            commandAndHandler.computeIfAbsent(cmdCls, k -> new HashSet<>()).add(handlerCls);
            logger.fine(() -> String.format("Resolved the abstract command [%s] with handler [%s]",
                cmdCls, handlerCls));
          } else {
            logger.warning(() -> String.format(
                "The command class [%s] extract from handler [%s] must be a concrete class", cmdCls,
                handlerCls));
          }
        }
      }
    }
  }

  synchronized void onBeforeShutdown(
      @Observes @Priority(Priorities.FRAMEWORK_LOWER) BeforeShutdown bs) {
    commandAndHandler.clear();
    logger.fine(() -> "Clear command & handlers cache.");
  }

  void validate(@Observes AfterDeploymentValidation adv, BeanManager bm) {
    List<Pair<Class<?>, Set<Class<?>>>> errs = new ArrayList<>();
    commandAndHandler.forEach((k, v) -> {
      if (v.size() > 1) {
        errs.add(Pair.of(k, v));
      }
    });
    if (errs.size() > 0) {
      StringBuilder errMsg = new StringBuilder("The command & handler mismatching:");
      errs.forEach(e -> {
        errMsg.append("\n  ").append(e.key().getName()).append(" -> ")
            .append(String.join(",",
                e.getValue().stream().map(Class::getName).collect(Collectors.toList())))
            .append(";");
      });
      errs.clear();
      logger.warning(() -> errMsg.toString());
      // TODO FIXME since we are using CDI, so the command hander bean may have qualifiers
      // adv.addDeploymentProblem(new CorantRuntimeException(errMsg.toString()));
    }
    if (!commandAndHandler.isEmpty()) {
      logger.fine(() -> String.format("Found %s command handlers", commandAndHandler.size()));
    }
  }

  private Class<?> resolveCommandType(Class<?> refCls) {
    Class<?> resolvedClass = null;
    Class<?> referenceClass = refCls;
    do {
      if (referenceClass.getGenericSuperclass() instanceof ParameterizedType) {
        resolvedClass = (Class<?>) ((ParameterizedType) referenceClass.getGenericSuperclass())
            .getActualTypeArguments()[0];
        break;
      } else {
        Type[] genericInterfaces = referenceClass.getGenericInterfaces();
        for (Type type : genericInterfaces) {
          if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType() instanceof Class) {
              resolvedClass = (Class<?>) parameterizedType.getActualTypeArguments()[0];
              break;
            }
          }
        }
      }
    } while (resolvedClass == null && (referenceClass = referenceClass.getSuperclass()) != null);
    return resolvedClass;
  }
}
