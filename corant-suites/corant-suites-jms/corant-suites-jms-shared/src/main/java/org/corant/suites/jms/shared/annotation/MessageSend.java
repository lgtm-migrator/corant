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
package org.corant.suites.jms.shared.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.enterprise.util.AnnotationLiteral;

/**
 * corant-suites-jms-shared
 *
 * @author bingo 下午3:49:53
 *
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface MessageSend {

  String connectionFactory() default "";

  String destination();

  boolean multicast()

  default false;

  int sessionModel() default 1;

  public static class MessageSenderLiteral extends AnnotationLiteral<MessageSend>
      implements MessageSend {

    private static final long serialVersionUID = 7391504689355513463L;

    final String destination;
    final boolean multicast;
    final String connectionFactory;
    final int sessionModel;

    /**
     * @param connectionFactory
     * @param sessionModel
     * @param multicast
     * @param destination
     */
    private MessageSenderLiteral(String connectionFactory, int sessionModel, boolean multicast,
        String destination) {
      super();
      this.destination = destination;
      this.multicast = multicast;
      this.connectionFactory = connectionFactory;
      this.sessionModel = sessionModel;
    }

    public static MessageSenderLiteral of(MessageSend p) {
      return new MessageSenderLiteral(p.connectionFactory(), p.sessionModel(), p.multicast(),
          p.destination());
    }

    public static MessageSenderLiteral of(String connectionFactory, int sessionModel,
        boolean multicast, String destination) {
      return new MessageSenderLiteral(connectionFactory, sessionModel, multicast, destination);
    }

    @Override
    public String connectionFactory() {
      return connectionFactory;
    }

    @Override
    public String destination() {
      return destination;
    }

    @Override
    public boolean multicast() {
      return multicast;
    }

    @Override
    public int sessionModel() {
      return sessionModel;
    }

  }
}
