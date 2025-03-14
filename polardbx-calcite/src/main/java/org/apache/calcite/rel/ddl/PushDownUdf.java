/*
 * Copyright [2013-2021], Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.calcite.rel.ddl;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.DDL;
import org.apache.calcite.sql.SqlNode;

import java.util.List;

public class PushDownUdf extends DDL {
    protected PushDownUdf(RelOptCluster cluster, RelTraitSet traits, RelNode input, SqlNode sqlNode,
                          SqlNode tableNameNode) {
        super(cluster, traits, input);
        this.sqlNode = sqlNode;
        this.setTableName(tableNameNode);
    }

    public static PushDownUdf create(RelOptCluster cluster, RelNode input, SqlNode sqlNode, SqlNode tableName) {
        return new PushDownUdf(cluster, cluster.traitSet(), input, sqlNode, tableName);
    }

    @Override
    public PushDownUdf copy(RelTraitSet traitSet, List<RelNode> inputs) {
        assert traitSet.containsIfApplicable(Convention.NONE);
        return new PushDownUdf(this.getCluster(),
            traitSet,
            inputs.get(0),
            this.sqlNode,
            getTableName());
    }
}
