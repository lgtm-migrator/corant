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
package org.corant.context.qualifier;

import static org.corant.shared.util.Strings.EMPTY;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;

/**
 * corant-context
 *
 * @author bingo 下午8:44:38
 *
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface Naming {

  NamingLiteral INSTANCE = new NamingLiteral(EMPTY);

  @Nonbinding
  String value() default EMPTY;

  class NamingLiteral extends AnnotationLiteral<Naming> implements Naming {

    private static final long serialVersionUID = 2627528079165566439L;

    private final String value;

    private NamingLiteral(String value) {
      this.value = value;
    }

    public static NamingLiteral of(String value) {
      return new NamingLiteral(value);
    }

    @Override
    public String value() {
      return value;
    }

  }
}
