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
package org.corant;

import static org.corant.shared.normal.Names.applicationName;
import static org.corant.shared.util.Assertions.shouldBeTrue;
import static org.corant.shared.util.CollectionUtils.setOf;
import static org.corant.shared.util.StreamUtils.streamOf;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.corant.kernel.boot.CorantRunner;
import org.corant.kernel.event.CorantLifecycleEvent.LifecycleEventEmitter;
import org.corant.kernel.event.PostContainerStartedEvent;
import org.corant.kernel.event.PostCorantReadyEvent;
import org.corant.kernel.event.PreContainerStopEvent;
import org.corant.kernel.spi.CorantBootHandler;
import org.corant.shared.exception.CorantRuntimeException;
import org.corant.shared.normal.Names;
import org.corant.shared.util.LaunchUtils;
import org.corant.shared.util.StopWatch;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.manager.api.WeldManager;

/**
 * corant-kernel
 *
 * <p>
 * Class that can be used to bootstrap and launch a Corant application from a Java main method. By
 * default class will perform the following steps to bootstrap your application:
 * <ul>
 * <li>Execute the boot preprocessor to handle some works before CDI container start, the works like
 * set some appropriate configuration properties to intervene system running.</li>
 * <li>Configure appropriate class loader to the current thread context class loader and CDI
 * container class loader and add configuration classes to the set of bean classes for the synthetic
 * bean archive if necessary.</li>
 * <li>Construct the CDI container and initialize it, after the CDI container initialized then fire
 * PostContainerStartedEvent to listeners, those listeners may be use to configure some components
 * after CDI initialized such as web server.</li>
 * <li>After the above execution was completed, fire PostCorantReadyEvent to listeners.</li>
 * </ul>
 * </p>
 *
 * <p>
 * In most circumstances the static {@link #run(Class, String[])} method can be called directly from
 * your {@literal main} method to bootstrap your application:
 *
 * <pre>
 * public class MyApplication {
 *   // ... Bean definitions
 *   public static void main(String[] arguments) throws Exception {
 *     try(Corant corant = Corant.run(MyApplication.class, arguments)){
 *      //... some works in CDI
 *   }
 * }
 * </pre>
 *
 * OR
 *
 * <pre>
 * public class MyApplication {
 *   // ... Bean definitions
 *   public static void main(String[] arguments) throws Exception {
 *    Corant corant = Corant.run(MyApplication.class, arguments)
 *   }
 * }
 * </pre>
 *
 * OR
 *
 * <pre>
 * public class MyApplication {
 *   // ... Bean definitions
 *   public static void main(String[] arguments) throws Exception {
 *     try (Corant corant =
 *         Corant.run(new Class[] {MyApplication.class, MyAnother.class}, arguments)) {
 *     }
 *   }
 * }
 * </pre>
 * <p>
 *
 * @see Corant#Corant(String...)
 * @see Corant#Corant(Class, String...)
 * @see Corant#Corant(ClassLoader, String...)
 * @see Corant#Corant(Class[], ClassLoader, String...)
 * @see CorantBootHandler
 * @see ApplicationConfigSourceProvider
 * @see ApplicationAdjustConfigSourceProvider
 * @see ApplicationProfileConfigSourceProvider
 *
 * @author bingo 上午11:52:09
 *
 */
public class Corant implements AutoCloseable {

  public static final String DISABLE_BOOST_LINE_CMD = "-disable_boost-line";
  public static final String DISABLE_BEFORE_START_HANDLER_CMD = "-disable_before-start-handler";
  public static final String DISABLE_AFTER_STARTED_HANDLER_CMD = "-disable_after-started-handler";
  public static final String REGISTER_TO_MBEAN_CMD = "-register_to_mbean";

  private static volatile Corant me; // NOSONAR

  private final Class<?>[] beanClasses;
  private final String[] arguments;
  private final ClassLoader classLoader;
  private WeldContainer container;
  private volatile CorantRunner mbeanRunner;

  /**
   * Use the class loader of Corant.class as current thread context and the CDI container class
   * loader.
   */
  public Corant() {
    this(null, null, new String[0]);
  }

  /**
   * Use given config class and arguments construct Corant instance. If the given config class is
   * not null then the class loader of the current thread context and the CDI container will be set
   * with the given config class class loader. The given arguments will be propagate to all
   * CorantBootHandler and all CorantLifecycleEvent listeners.
   *
   * @see #Corant(Class, ClassLoader, String...)
   * @param configClass
   * @param arguments
   */
  public Corant(Class<?> configClass, String... arguments) {
    this(configClass == null ? null : new Class<?>[] {configClass},
        configClass != null ? configClass.getClassLoader() : null, arguments);
  }

  /**
   * Construct Coarnt instance with given bean classes and class loader and arguments. If the given
   * bean classes are not null then they will be added to the set of bean classes for the synthetic
   * bean archive. If the given class loader is not null then it will be set to the context
   * ClassLoader for current Thread and the CDI container class loader else we use Corant.class
   * class loader. The given arguments will be propagate to all CorantBootHandler and all
   * CorantLifecycleEvent listeners.
   *
   * @param beanClasses
   * @param classLoader
   * @param arguments
   */
  public Corant(Class<?>[] beanClasses, ClassLoader classLoader, String... arguments) {
    this.beanClasses =
        beanClasses == null ? new Class[0] : Arrays.copyOf(beanClasses, beanClasses.length);
    if (classLoader != null) {
      this.classLoader = classLoader;
    } else {
      this.classLoader = Corant.class.getClassLoader();
    }
    this.arguments = arguments;
    setMe(this);
  }

  /**
   * Use given class loader and arguments to construct Corant instance. If the given class loader is
   * not null then the class loader of the current thread context and the CDI container will be set
   * with the given class loader.The given arguments will be propagate to all CorantBootHandler and
   * all CorantLifecycleEvent listeners.
   *
   * @param classLoader
   * @param arguments
   */
  public Corant(ClassLoader classLoader, String... arguments) {
    this(null, classLoader, arguments);
  }

  /**
   * Use given arguments and the class loader of Corant.class to construct Corant instance.The given
   * arguments will be propagate to all CorantBootHandler and all CorantLifecycleEvent listeners.
   *
   * @param arguments
   */
  public Corant(String... arguments) {
    this(null, null, arguments);
  }

  public static <T> T call(boolean synthetic, Class<T> beanClass, Annotation[] annotations,
      String[] arguments) {
    if (current() == null) {
      if (synthetic) {
        run(beanClass, arguments);
      } else {
        run(new Class[0], arguments);
      }
    }
    return CDI.current().select(beanClass, annotations).get();
  }

  public static <T> T call(Class<T> beanClass, String... arguments) {
    return call(false, beanClass, new Annotation[0], arguments);
  }

  public static <T> T callSynthetic(Class<T> beanClass, String... arguments) {
    return call(true, beanClass, new Annotation[0], arguments);
  }

  public static Corant current() {
    return me;
  }

  public static synchronized Corant run(Class<?> configClass, String... arguments) {
    Corant corant = new Corant(configClass, arguments);
    corant.start(null);
    return corant;
  }

  public static synchronized Corant run(Class<?>[] beanClasses) {
    Corant corant = new Corant(beanClasses, null);
    corant.start(null);
    return corant;
  }

  public static synchronized Corant run(Class<?>[] beanClasses, String... arguments) {
    Corant corant = new Corant(beanClasses, null, arguments);
    corant.start(null);
    return corant;
  }

  public static synchronized Corant run(ClassLoader classLoader, Consumer<Weld> preInitializer,
      String... arguments) {
    Corant corant = new Corant(classLoader, arguments);
    corant.start(preInitializer);
    return corant;
  }

  private static synchronized void setMe(Corant me) {
    if (me != null) {
      shouldBeTrue(Corant.me == null, "We already have an instance of Corant. Don't repeat it!");
    }
    Corant.me = me;
  }

  public Corant accept(Consumer<Corant> consumer) {
    if (consumer != null) {
      consumer.accept(this);
    }
    return this;
  }

  public <R> R apply(Function<Corant, R> function) {
    if (function != null) {
      return function.apply(this);
    }
    return null;
  }

  @Override
  public void close() throws Exception {
    stop();
  }

  /**
   * This method is not normally used, and should be use CDI.current().getBeanManager(), except to
   * take advantage of some of the features of Weld.
   *
   * @return getBeanManager
   */
  public synchronized WeldManager getBeanManager() {
    shouldBeTrue(isRuning(), "The corant instance is null or is not in running");
    return (WeldManager) container.getBeanManager();
  }

  /**
   *
   * @return the classLoader
   */
  public ClassLoader getClassLoader() {
    return classLoader;
  }

  public synchronized Object getId() {
    return container.getId();
  }

  public synchronized boolean isRuning() {
    return container != null && container.isRunning();
  }

  public synchronized void start(Consumer<Weld> preInitializer) {
    if (isRuning()) {
      return;
    }
    Thread.currentThread().setContextClassLoader(classLoader);
    StopWatch stopWatch = StopWatch.press(applicationName(),
        "Starting ".concat(applicationName()).concat(", perform the spi prestart handlers."));
    doBeforeStart(classLoader);

    final Logger logger = Logger.getLogger(Corant.class.getName());
    stopWatch.stop(tk -> log(logger, "%s in %s seconds.", tk.getName(), tk.getTimeSeconds()))
        .start("Initializes the CDI container");
    if (registerMBean()) {
      log(logger,
          "Register %s to MBean server, one can use it for shutdown or restartup the application.",
          applicationName(), Instant.now());
    }
    initializeContainer(preInitializer);

    stopWatch.stop(tk -> log(logger, "%s in %s seconds.", tk.getName(), tk.getTimeSeconds()))
        .start("Initializes all suites");
    doAfterContainerInitialized();

    stopWatch.stop(tk -> log(logger, "%s in %s seconds ", tk.getName(), tk.getTimeSeconds()))
        .start("Perform the spi handlers after startup.");
    doAfterStarted(classLoader);

    stopWatch.stop(tk -> log(logger, "%s in %s seconds.", tk.getName(), tk.getTimeSeconds()))
        .destroy(sw -> {
          double tt = sw.getTotalTimeSeconds();
          if (tt > 8) {
            log(logger,
                "Finished all initialization at %s, take %s seconds. It's been a long way, but we're here.",
                Instant.now(), tt);
          } else {
            log(logger, "Finished all initialization at %s, take %s seconds.", Instant.now(), tt);
          }
        });

    // log(logger, "Finished at: %s.", Instant.now());
    log(logger,
        "Final memory: %sM/%sM/%sM, process id: %s, java version: %s, default locale: %s, default timezone: %s.%s",
        LaunchUtils.getUsedMemoryMb(), LaunchUtils.getTotalMemoryMb(), LaunchUtils.getMaxMemoryMb(),
        LaunchUtils.getPid(), LaunchUtils.getJavaVersion(), Locale.getDefault(),
        TimeZone.getDefault().getID(), boostLine());

    doOnReady();
  }

  public synchronized void stop() {
    if (isRuning()) {
      LifecycleEventEmitter emitter = container.select(LifecycleEventEmitter.class).get();
      emitter.fire(new PreContainerStopEvent(arguments));
      container.close();
    }
    container = null;
    log(Logger.getLogger(Corant.class.getName()), "Stopped %s at %s", applicationName(),
        Instant.now());
  }

  void doAfterContainerInitialized() {
    LifecycleEventEmitter emitter = container.select(LifecycleEventEmitter.class).get();
    emitter.fire(new PostContainerStartedEvent(arguments));
  }

  void doAfterStarted(ClassLoader classLoader) {
    if (setOf(arguments).contains(DISABLE_AFTER_STARTED_HANDLER_CMD)) {
      return;
    }
    streamOf(ServiceLoader.load(CorantBootHandler.class, classLoader))
        .sorted(CorantBootHandler::compare)
        .forEach(h -> h.handleAfterStarted(this, Arrays.copyOf(arguments, arguments.length)));
  }

  void doBeforeStart(ClassLoader classLoader) {
    if (setOf(arguments).contains(DISABLE_BEFORE_START_HANDLER_CMD)) {
      return;
    }
    streamOf(ServiceLoader.load(CorantBootHandler.class, classLoader))
        .sorted(CorantBootHandler::compare)
        .forEach(h -> h.handleBeforeStart(classLoader, Arrays.copyOf(arguments, arguments.length)));
  }

  void doOnReady() {
    LifecycleEventEmitter emitter = container.select(LifecycleEventEmitter.class).get();
    emitter.fire(new PostCorantReadyEvent(arguments));
  }

  synchronized void initializeContainer(Consumer<Weld> preInitializer) {
    String id = Names.applicationName().concat("-weld-").concat(UUID.randomUUID().toString());
    Weld weld = new Weld(id);
    weld.setClassLoader(classLoader);
    weld.addExtensions(new CorantExtension());
    if (beanClasses != null) {
      weld.addBeanClasses(beanClasses);
    }
    if (preInitializer != null) {
      preInitializer.accept(weld);
    }
    container = weld.addProperty(Weld.SHUTDOWN_HOOK_SYSTEM_PROPERTY, true).initialize();
  }

  boolean registerMBean() {
    if (!setOf(arguments).contains(REGISTER_TO_MBEAN_CMD)) {
      return false;
    }
    synchronized (this) {
      if (mbeanRunner == null) {
        mbeanRunner = new CorantRunner(beanClasses, arguments);
        ObjectName objectName = null;
        try {
          objectName = new ObjectName(applicationName() + ":type=basic,name=CorantRunner");
        } catch (MalformedObjectNameException ex) {
          throw new CorantRuntimeException(ex);
        }
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
          server.registerMBean(mbeanRunner, objectName);
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException
            | NotCompliantMBeanException ex) {
          throw new CorantRuntimeException(ex);
        }
      }
      return true;
    }
  }

  private String boostLine() {
    if (!setOf(arguments).contains(DISABLE_BOOST_LINE_CMD)) {
      String spLine = "--------------------------------------------------";
      return "\n".concat(spLine).concat(spLine);
    }
    return "";
  }

  private void log(Logger logger, String msgOrFmt, Object... arguments) {
    if (arguments.length > 0) {
      logger.info(() -> String.format(msgOrFmt, arguments));
    } else {
      logger.info(() -> msgOrFmt);
    }
  }

  class CorantExtension implements Extension {
    void onAfterBeanDiscovery(@Observes AfterBeanDiscovery event) {
      event.addBean().addType(Corant.class).scope(ApplicationScoped.class)
          .addQualifier(Default.Literal.INSTANCE).addQualifier(Any.Literal.INSTANCE)
          .produceWith(obj -> Corant.this);
    }
  }
}
