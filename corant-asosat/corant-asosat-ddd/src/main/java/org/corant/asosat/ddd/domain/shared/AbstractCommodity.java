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
package org.corant.asosat.ddd.domain.shared;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AbstractCommodity extends AbstractPPTReference implements Numbered {

  private static final long serialVersionUID = 3565638036145351917L;

  @Column
  private String remark;

  protected AbstractCommodity() {}

  protected AbstractCommodity(Long id, Long vn, String number, String name, String remark) {
    super(id, vn, name, number);
    this.remark = remark;
  }

  public String getRemark() {
    return remark;
  }

  protected void setRemark(String remark) {
    this.remark = remark;
  }

}
