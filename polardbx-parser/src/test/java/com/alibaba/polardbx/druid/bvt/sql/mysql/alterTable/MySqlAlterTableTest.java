/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.polardbx.druid.bvt.sql.mysql.alterTable;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import com.alibaba.polardbx.druid.sql.parser.Token;
import junit.framework.TestCase;
import org.junit.Assert;

public class MySqlAlterTableTest extends TestCase {
    public void test_alter_0() throws Exception {
        String sql = "ALTER TABLE `test`.`tb1` CHANGE COLUMN `fname` `fname1` VARCHAR(45) NULL DEFAULT NULL  ;";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);
        String output = SQLUtils.toMySqlString(stmt);
        Assert.assertEquals("ALTER TABLE `test`.`tb1`\n\tCHANGE COLUMN `fname` `fname1` VARCHAR(45) NULL DEFAULT NULL;",
            output);
    }

    public void test_alter_1() throws Exception {
        String sql = "ALTER TABLE `test`.`tb1` CHARACTER SET = utf8 , COLLATE = utf8_general_ci ;";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);

        Assert.assertEquals("ALTER TABLE `test`.`tb1`\n\tCHARACTER SET = utf8 COLLATE = utf8_general_ci;",
            SQLUtils.toMySqlString(stmt));
        Assert.assertEquals("alter table `test`.`tb1`\n\tcharacter set = utf8 collate = utf8_general_ci;",
            SQLUtils.toMySqlString(stmt, SQLUtils.DEFAULT_LCASE_FORMAT_OPTION));
    }

    public void test_alter_2() throws Exception {
        String sql = "ALTER TABLE `test`.`tb1` ADD INDEX `f` (`fname` ASC) ;";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);
        String output = SQLUtils.toMySqlString(stmt);
        Assert.assertEquals("ALTER TABLE `test`.`tb1`\n\tADD INDEX `f` (`fname` ASC);", output);
    }

    public void test_alter_3() throws Exception {
        String sql = "ALTER TABLE `test`.`tb1` ENGINE = InnoDB ;";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);
        String output = SQLUtils.toMySqlString(stmt);
        Assert.assertEquals("ALTER TABLE `test`.`tb1`\n\tENGINE = InnoDB;", output);
    }

    public void test_alter_4() throws Exception {
        String sql = "ALTER TABLE `test`.`tb1` COLLATE = utf8_general_ci , PACK_KEYS = Pack All , ENGINE = InnoDB ;";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);
        String output = SQLUtils.toMySqlString(stmt);
        Assert.assertEquals(
            "ALTER TABLE `test`.`tb1`\n\tCOLLATE = utf8_general_ci PACK_KEYS = PACK ALL ENGINE = InnoDB;", output);
    }

    public void test_alter_5() throws Exception {
        String sql = "ALTER TABLE t1 COMMENT '表的注释';";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);
        String output = SQLUtils.toMySqlString(stmt);
        Assert.assertEquals("ALTER TABLE t1\n\tCOMMENT = '表的注释';", output);
    }

    public void test_alter_6() throws Exception {
        String sql = "ALTER TABLE `test`.`tb1` DEFAULT CHARACTER SET utf8 COLLATE = utf8_general_ci ;";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);

        //System.out.println(SQLUtils.toMySqlString(stmt, SQLUtils.DEFAULT_LCASE_FORMAT_OPTION));
        Assert.assertEquals("ALTER TABLE `test`.`tb1`\n\tCHARACTER SET = utf8 COLLATE utf8_general_ci;",
            SQLUtils.toMySqlString(stmt));
        Assert.assertEquals("alter table `test`.`tb1`\n\tcharacter set = utf8 collate utf8_general_ci;",
            SQLUtils.toMySqlString(stmt, SQLUtils.DEFAULT_LCASE_FORMAT_OPTION));
    }

    public void test_alter_7() throws Exception {
        String sql = "ALTER TABLE `event_record`\n"
            + "	ADD COLUMN `notifyId` varchar(32) NOT NULL COMMENT '消息通知ID' AFTER `id`,\n"
            + "	MODIFY COLUMN `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '事件类型：order_pay，refund，refund_finish' AFTER `event_no`,\n"
            + "	MODIFY COLUMN `biz_type` tinyint NOT NULL COMMENT '业务类型：1订单，2履约，3退款' AFTER `event_type`,\n"
            + "	MODIFY COLUMN `user_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户id' AFTER `biz_no` WITH TABLEGROUP=tg28 IMPLICIT";
        MySqlStatementParser parser = new MySqlStatementParser(sql);
        SQLStatement stmt = parser.parseStatementList().get(0);
        parser.match(Token.EOF);
        Assert.assertEquals(sql,
            SQLUtils.toMySqlString(stmt));
    }

}
