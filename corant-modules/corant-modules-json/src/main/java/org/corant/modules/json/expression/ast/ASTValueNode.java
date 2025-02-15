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
package org.corant.modules.json.expression.ast;

import org.corant.modules.json.expression.EvaluationContext;

/**
 * corant-modules-json
 *
 * @author bingo 下午5:04:44
 *
 */
public class ASTValueNode implements ASTNode<Object> {

  protected final Object value;

  public ASTValueNode(Object value) {
    this.value = value;
  }

  @Override
  public ASTNodeType getType() {
    return ASTNodeType.VAL;
  }

  @Override
  public Object getValue(EvaluationContext ctx) {
    return value;
  }

}
