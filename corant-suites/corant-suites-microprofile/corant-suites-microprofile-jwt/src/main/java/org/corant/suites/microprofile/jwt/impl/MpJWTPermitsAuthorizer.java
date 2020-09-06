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
package org.corant.suites.microprofile.jwt.impl;

import static org.corant.shared.util.Empties.isNotEmpty;
import java.util.function.Predicate;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * corant-suites-microprofile-jwt
 *
 * @author bingo 12:36:33
 *
 */
@ApplicationScoped
public class MpJWTPermitsAuthorizer extends AbstractMpJWTAuthorizer {

  @Override
  protected boolean isAllowed(Predicate<String> p, JsonWebToken jwt) {
    if (isNotEmpty(jwt.getGroups())) {
      return jwt.getGroups().stream().anyMatch(p);
    }
    return false;
  }

}
