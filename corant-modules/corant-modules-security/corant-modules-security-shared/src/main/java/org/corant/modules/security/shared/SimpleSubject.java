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
package org.corant.modules.security.shared;

import static org.corant.shared.util.Lists.newArrayList;
import static org.corant.shared.util.Objects.areEqual;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.corant.modules.security.Permission;
import org.corant.modules.security.Principal;
import org.corant.modules.security.Role;
import org.corant.modules.security.Subject;

/**
 * corant-modules-security-shared
 *
 * @author bingo 下午4:22:33
 *
 */
public class SimpleSubject implements Subject, AttributeSet {

  private static final long serialVersionUID = -2622944157923465303L;

  protected Collection<Principal> principals;

  protected Collection<Role> roles = Collections.emptyList();

  protected Collection<Permission> permissions = Collections.emptyList();

  protected Map<String, ? extends Serializable> attributes = Collections.emptyMap();

  public SimpleSubject(Collection<? extends Principal> principals) {
    this.principals = Collections.unmodifiableCollection(newArrayList(principals));
  }

  public SimpleSubject(Collection<? extends Principal> principals, Collection<? extends Role> roles,
      Collection<? extends Permission> permissions,
      Map<String, ? extends Serializable> attributes) {
    this.principals = Collections.unmodifiableCollection(newArrayList(principals));
    this.roles = Collections.unmodifiableCollection(newArrayList(roles));
    this.permissions = Collections.unmodifiableCollection(newArrayList(permissions));
    if (attributes != null) {
      this.attributes = Collections.unmodifiableMap(attributes);
    }
  }

  public SimpleSubject(Collection<? extends Principal> principals, Collection<? extends Role> roles,
      Map<String, ? extends Serializable> attributes) {
    this(principals, roles, null, attributes);
  }

  protected SimpleSubject() {}

  @Override
  public Map<String, ? extends Serializable> getAttributes() {
    return attributes;
  }

  public Collection<? extends Permission> getPermissions() {
    return permissions;
  }

  @Override
  public Principal getPrincipal(String name) {
    return principals.stream().filter(p -> areEqual(name, p.getName())).findFirst().orElse(null);
  }

  @Override
  public Collection<? extends Principal> getPrincipals() {
    return principals;
  }

  public Collection<? extends Role> getRoles() {
    return roles;
  }

  @Override
  public <T> T unwrap(Class<T> cls) {
    if (SimpleSubject.class.isAssignableFrom(cls)) {
      return cls.cast(this);
    }
    return Subject.super.unwrap(cls);
  }
}
