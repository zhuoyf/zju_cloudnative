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
package com.alibaba.polardbx.druid.sql.dialect.mysql.parser;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.*;
import com.alibaba.polardbx.druid.sql.ast.expr.*;
import com.alibaba.polardbx.druid.sql.ast.statement.*;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLAnnIndex;
import com.alibaba.polardbx.druid.sql.ast.SQLCurrentTimeExpr;
import com.alibaba.polardbx.druid.sql.ast.SQLCurrentUserExpr;
import com.alibaba.polardbx.druid.sql.ast.SQLDataType;
import com.alibaba.polardbx.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.polardbx.druid.sql.ast.SQLExpr;
import com.alibaba.polardbx.druid.sql.ast.SQLName;
import com.alibaba.polardbx.druid.sql.ast.SQLOrderBy;
import com.alibaba.polardbx.druid.sql.ast.SQLOrderingSpecification;
import com.alibaba.polardbx.druid.sql.ast.SQLPartition;
import com.alibaba.polardbx.druid.sql.ast.SQLPartitionValue;
import com.alibaba.polardbx.druid.sql.ast.SQLSubPartition;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLArrayExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLExtractExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLHexExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIntervalExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIntervalUnit;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLListExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLQueryExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLUnaryExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLUnaryOperator;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableModifyPartitionValues;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAssignItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDDLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLForeignKeyImpl.Option;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelect;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLValuesQuery;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MysqlForeignKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlCharExpr;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlOrderingExpr;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlOutFileExpr;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.expr.MySqlUserName;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.parser.ByteString;
import com.alibaba.polardbx.druid.sql.parser.Lexer;
import com.alibaba.polardbx.druid.sql.parser.ParserException;
import com.alibaba.polardbx.druid.sql.parser.SQLExprParser;
import com.alibaba.polardbx.druid.sql.parser.SQLParserFeature;
import com.alibaba.polardbx.druid.sql.parser.SQLSelectParser;
import com.alibaba.polardbx.druid.sql.parser.Token;
import com.alibaba.polardbx.druid.util.FnvHash;
import com.alibaba.polardbx.druid.util.MySqlUtils;
import com.alibaba.polardbx.druid.util.StringUtils;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class MySqlExprParser extends SQLExprParser {
    public final static String[] AGGREGATE_FUNCTIONS;

    public final static long[] AGGREGATE_FUNCTIONS_CODES;

    public final static String[] SINGLE_WORD_TABLE_OPTIONS;

    public final static long[] SINGLE_WORD_TABLE_OPTIONS_CODES;

    static {
        String[] strings = {
            "AVG",
            "ANY_VALUE",
            "BIT_AND",
            "BIT_OR",
            "BIT_XOR",
            "COUNT",
            "GROUP_CONCAT",
            "LISTAGG",
            "MAX",
            "MIN",
            "STD",
            "STDDEV",
            "STDDEV_POP",
            "STDDEV_SAMP",
            "SUM",
            "VAR_SAMP",
            "VARIANCE",
            "JSON_ARRAYAGG",
            "JSON_OBJECTAGG",
            "HYPERLOGLOG",
            "CHECK_SUM"
        };

        AGGREGATE_FUNCTIONS_CODES = FnvHash.fnv1a_64_lower(strings, true);
        AGGREGATE_FUNCTIONS = new String[AGGREGATE_FUNCTIONS_CODES.length];
        for (String str : strings) {
            long hash = FnvHash.fnv1a_64_lower(str);
            int index = Arrays.binarySearch(AGGREGATE_FUNCTIONS_CODES, hash);
            AGGREGATE_FUNCTIONS[index] = str;
        }

        // https://dev.mysql.com/doc/refman/5.7/en/create-table.html
        String[] options = {
            "AUTO_INCREMENT", "AVG_ROW_LENGTH", /*"CHARACTER SET",*/ "CHECKSUM", "COLLATE", "COMMENT",
            "COMPRESSION", "CONNECTION", /*"{DATA|INDEX} DIRECTORY",*/ "DELAY_KEY_WRITE", "ENCRYPTION", "ENGINE",
            "INSERT_METHOD", "KEY_BLOCK_SIZE", "MAX_ROWS", "MIN_ROWS", "PACK_KEYS", "PASSWORD", "ROW_FORMAT",
            "STATS_AUTO_RECALC", "STATS_PERSISTENT", "STATS_SAMPLE_PAGES", "TABLESPACE", "UNION",
            // Extra added.
            "BLOCK_FORMAT", "STORAGE_TYPE", "STORAGE_POLICY"};
        SINGLE_WORD_TABLE_OPTIONS_CODES = FnvHash.fnv1a_64_lower(options, true);
        SINGLE_WORD_TABLE_OPTIONS = new String[SINGLE_WORD_TABLE_OPTIONS_CODES.length];
        for (String str : options) {
            long hash = FnvHash.fnv1a_64_lower(str);
            int index = Arrays.binarySearch(SINGLE_WORD_TABLE_OPTIONS_CODES, hash);
            SINGLE_WORD_TABLE_OPTIONS[index] = str;
        }
    }

    public MySqlExprParser(Lexer lexer) {
        super(lexer, DbType.mysql);
        this.aggregateFunctions = AGGREGATE_FUNCTIONS;
        this.aggregateFunctionHashCodes = AGGREGATE_FUNCTIONS_CODES;
    }

    public MySqlExprParser(ByteString sql) {
        this(new MySqlLexer(sql));
        this.lexer.nextToken();
    }

    @Deprecated
    public MySqlExprParser(String sql) {
        this(ByteString.from(sql));
    }

    public MySqlExprParser(ByteString sql, SQLParserFeature... features) {
        super(new MySqlLexer(sql, features), DbType.mysql);
        this.aggregateFunctions = AGGREGATE_FUNCTIONS;
        this.aggregateFunctionHashCodes = AGGREGATE_FUNCTIONS_CODES;
        if (sql.length() > 6) {
            char c0 = sql.charAt(0);
            char c1 = sql.charAt(1);
            char c2 = sql.charAt(2);
            char c3 = sql.charAt(3);
            char c4 = sql.charAt(4);
            char c5 = sql.charAt(5);
            char c6 = sql.charAt(6);

            if (c0 == 'S' && c1 == 'E' && c2 == 'L' && c3 == 'E' && c4 == 'C' && c5 == 'T' && c6 == ' ') {
                lexer.reset(6, ' ', Token.SELECT);
                return;
            }

            if (c0 == 's' && c1 == 'e' && c2 == 'l' && c3 == 'e' && c4 == 'c' && c5 == 't' && c6 == ' ') {
                lexer.reset(6, ' ', Token.SELECT);
                return;
            }

            if (c0 == 'I' && c1 == 'N' && c2 == 'S' && c3 == 'E' && c4 == 'R' && c5 == 'T' && c6 == ' ') {
                lexer.reset(6, ' ', Token.INSERT);
                return;
            }

            if (c0 == 'i' && c1 == 'n' && c2 == 's' && c3 == 'e' && c4 == 'r' && c5 == 't' && c6 == ' ') {
                lexer.reset(6, ' ', Token.INSERT);
                return;
            }

            if (c0 == 'U' && c1 == 'P' && c2 == 'D' && c3 == 'A' && c4 == 'T' && c5 == 'E' && c6 == ' ') {
                lexer.reset(6, ' ', Token.UPDATE);
                return;
            }

            if (c0 == 'u' && c1 == 'p' && c2 == 'd' && c3 == 'a' && c4 == 't' && c5 == 'e' && c6 == ' ') {
                lexer.reset(6, ' ', Token.UPDATE);
                return;
            }

            if (c0 == '/' && c1 == '*' && (isEnabled(SQLParserFeature.OptimizedForParameterized) && !isEnabled(
                SQLParserFeature.TDDLHint))) {
                MySqlLexer mySqlLexer = (MySqlLexer) lexer;
                mySqlLexer.skipFirstHintsOrMultiCommentAndNextToken();
                return;
            }
        }
        this.lexer.nextToken();

    }

    @Deprecated
    public MySqlExprParser(String sql, SQLParserFeature... features) {
        this(ByteString.from(sql), features);
    }

    public MySqlExprParser(ByteString sql, boolean keepComments) {
        this(new MySqlLexer(sql, true, keepComments));
        this.lexer.nextToken();
    }

    public MySqlExprParser(ByteString sql, boolean skipComment, boolean keepComments) {
        this(new MySqlLexer(sql, skipComment, keepComments));
        this.lexer.nextToken();
    }

    public SQLExpr primary() {
        final Token tok = lexer.token();
        switch (tok) {
        case IDENTIFIER:
            final long hash_lower = lexer.hash_lower();
            Lexer.SavePoint savePoint = lexer.mark();

            if (hash_lower == FnvHash.Constants.OUTLINE) {
                lexer.nextToken();
                try {
                    SQLExpr file = primary();
                    SQLExpr expr = new MySqlOutFileExpr(file);

                    return primaryRest(expr);
                } catch (ParserException e) {
                    lexer.reset(savePoint);
                }
            }

            String strVal = lexer.stringVal();

            boolean quoteStart = strVal.length() > 0 && (strVal.charAt(0) == '`' || strVal.charAt(0) == '"');

            if (!quoteStart) {
                // Allow function in order by when not start with '`'.
                setAllowIdentifierMethod(true);
            }

            SQLCurrentTimeExpr currentTimeExpr = null;
            if (hash_lower == FnvHash.Constants.CURRENT_TIME && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.CURRENT_TIME);
            } else if (hash_lower == FnvHash.Constants.CURRENT_TIMESTAMP && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.CURRENT_TIMESTAMP);
            } else if (hash_lower == FnvHash.Constants.CURRENT_DATE && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.CURRENT_DATE);
            } else if (hash_lower == FnvHash.Constants.CURDATE && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.CURDATE);
            } else if (hash_lower == FnvHash.Constants.LOCALTIME && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.LOCALTIME);
            } else if (hash_lower == FnvHash.Constants.LOCALTIMESTAMP && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.LOCALTIMESTAMP);
            } else if (hash_lower == FnvHash.Constants.UTC_DATE && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.UTC_DATE);
            } else if (hash_lower == FnvHash.Constants.UTC_TIME && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.UTC_TIME);
            } else if (hash_lower == FnvHash.Constants.UTC_TIMESTAMP && !quoteStart) {
                currentTimeExpr = new SQLCurrentTimeExpr(SQLCurrentTimeExpr.Type.UTC_TIMESTAMP);
            } else if (isEnabled(SQLParserFeature.DrdsMisc)
                && FnvHash.Constants.MYSQL_CHARACTER_SETS.contains(hash_lower)
                && !quoteStart
            ) {
                String charset = lexer.hexString();
                lexer.nextToken();

                String hexString;
                if (lexer.token() == Token.LITERAL_HEX) {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else if (lexer.token() == Token.LITERAL_ALIAS) {
                    // string like _utf8mb4"abcd"
                    hexString = null;
                } else if (lexer.token() == Token.IDENTIFIER && !identifierEquals("X")) {
                    // string like _utf8mb4`abcd`
                    throw new ParserException("syntax error. " + lexer.info());
                } else {
                    acceptIdentifier("X");
                    hexString = lexer.stringVal();
                    accept(Token.LITERAL_CHARS);
                }

                MySqlCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    if (lexer.token() == Token.LITERAL_ALIAS) {
                        // string like _utf8mb4"abcd"
                        str = StringUtils.removeNameQuotes(lexer.stringVal());
                    }
                    byte[] binary;
                    if (hash_lower == FnvHash.Constants._BINARY) {
                        binary = lexer.binaryVal();
                    } else {
                        binary = null;
                    }

                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, charset);
                    charExpr.setBinary(binary);
                } else {
                    charExpr = new MySqlCharExpr(hexString, charset);
                    charExpr.setHex(true);
                }

                if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                    lexer.nextToken();
                    String collate = lexer.stringVal();
                    charExpr.setCollate(collate);
                    if (lexer.token() == Token.LITERAL_CHARS) {
                        lexer.nextToken();
                    } else {
                        accept(Token.IDENTIFIER);
                    }
                }

                return primaryRest(charExpr);
            } else if ((hash_lower == FnvHash.Constants._LATIN1) && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();

                    String collate = null;
                    if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                        lexer.nextToken();
                        collate = lexer.stringVal();
                        if (lexer.token() == Token.LITERAL_CHARS) {
                            lexer.nextToken();
                        } else {
                            accept(Token.IDENTIFIER);
                        }
                    }

                    charExpr = new MySqlCharExpr(str, "_latin1", collate);
                } else {
                    charExpr = new MySqlCharExpr(hexString, "_latin1");
                }

                return primaryRest(charExpr);
            } else if ((hash_lower == FnvHash.Constants._UTF8 || hash_lower == FnvHash.Constants._UTF8MB4)
                && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();

                    String collate = null;
                    if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                        lexer.nextToken();
                        collate = lexer.stringVal();
                        if (lexer.token() == Token.LITERAL_CHARS) {
                            lexer.nextToken();
                        } else {
                            accept(Token.IDENTIFIER);
                        }
                    }

                    charExpr = new MySqlCharExpr(str, "_utf8", collate);
                } else {
                    String str = MySqlUtils.utf8(hexString);
                    charExpr = new SQLCharExpr(str);
                }

                return primaryRest(charExpr);
            } else if ((hash_lower == FnvHash.Constants._UTF16 || hash_lower == FnvHash.Constants._UCS2)
                && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_utf16");
                } else {
                    charExpr = new SQLCharExpr(MySqlUtils.utf16(hexString));
                }

                return primaryRest(charExpr);
            } else if ((hash_lower == FnvHash.Constants._UTF16LE)
                && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_utf16le");
                } else {
                    charExpr = new MySqlCharExpr(hexString, "_utf16le");
                }

                return primaryRest(charExpr);
            } else if (hash_lower == FnvHash.Constants._UTF32 && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_utf32");
                } else {
                    charExpr = new SQLCharExpr(MySqlUtils.utf32(hexString));
                }

                return primaryRest(charExpr);
            } else if (hash_lower == FnvHash.Constants._GBK && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_gbk");
                } else {
                    charExpr = new SQLCharExpr(MySqlUtils.gbk(hexString));
                }

                return primaryRest(charExpr);
            } else if (hash_lower == FnvHash.Constants._UJIS && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_ujis");
                } else {
                    charExpr = new MySqlCharExpr(hexString, "_ujis");
                }

                return primaryRest(charExpr);
            } else if (hash_lower == FnvHash.Constants._BIG5 && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_big5");
                } else {
                    charExpr = new SQLCharExpr(MySqlUtils.big5(hexString));
                }

                return primaryRest(charExpr);
            } else if (lexer.identifierEquals("_gb18030") && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_gb18030");
                } else {
                    charExpr = new SQLCharExpr(MySqlUtils.gb18030(hexString));
                }

                return primaryRest(charExpr);
            } else if (lexer.identifierEquals("_ascii") && !quoteStart) {
                lexer.nextToken();

                String hexString;
                if (lexer.identifierEquals(FnvHash.Constants.X)) {
                    lexer.nextToken();
                    hexString = lexer.stringVal();
                    lexer.nextToken();
                } else if (lexer.token() == Token.LITERAL_CHARS) {
                    hexString = null;
                } else {
                    hexString = lexer.hexString();
                    lexer.nextToken();
                }

                SQLCharExpr charExpr;
                if (hexString == null) {
                    String str = lexer.stringVal();
                    lexer.nextToken();
                    charExpr = new MySqlCharExpr(str, "_ascii");
                } else {
                    charExpr = new SQLCharExpr(MySqlUtils.ascii(hexString));
                }

                return primaryRest(charExpr);
            } else if (lexer.identifierEquals("_binary") && !quoteStart) {
                // Alias BINARY expr.
                // ref: https://dev.mysql.com/doc/refman/5.7/en/charset-binary-set.html
                lexer.nextToken();
                if (lexer.token() == Token.COMMA || lexer.token() == Token.SEMI || lexer.token() == Token.EOF) {
                    return new SQLIdentifierExpr("BINARY");
                } else {
                    SQLUnaryExpr binaryExpr = new SQLUnaryExpr(SQLUnaryOperator.BINARY, primary());
                    return primaryRest(binaryExpr);
                }
            } else if (hash_lower == FnvHash.Constants.CURRENT_USER && isEnabled(
                SQLParserFeature.EnableCurrentUserExpr)) {
                String methodName = lexer.stringVal();
                lexer.nextToken();
                if (SQLUtils.enclosedByBackTick(methodName)) {
                    SQLIdentifierExpr expr = new SQLIdentifierExpr("CURRENT_USER");
                    expr.setEnclosedByBacktick(true);
                    return expr;
                }
                if (lexer.token() == Token.LPAREN) {
                    // Treated this as method invoke.
                    return primaryRest(methodRest(new SQLIdentifierExpr(methodName), true));
                } else {
                    return primaryRest(new SQLCurrentUserExpr());
                }
            } else if (hash_lower == -5808529385363204345L && lexer.charAt(lexer.pos()) == '\'') { // hex
                lexer.nextToken();
                SQLHexExpr hex = new SQLHexExpr(lexer.stringVal());
                lexer.nextToken();
                return primaryRest(hex);
            }

            if (currentTimeExpr != null) {
                String methodName = lexer.stringVal();
                lexer.nextToken();

                if (lexer.token() == Token.LPAREN) {
                    lexer.nextToken();
                    if (lexer.token() == Token.LPAREN) {
                        lexer.nextToken();
                    } else {
                        return primaryRest(
                            methodRest(new SQLIdentifierExpr(methodName), false)
                        );
                    }
                }

                return primaryRest(currentTimeExpr);
            }

            return super.primary();
        case VARIANT:
            SQLVariantRefExpr varRefExpr = new SQLVariantRefExpr(lexer.stringVal());
            lexer.nextToken();
            if (varRefExpr.getName().equalsIgnoreCase("@@global")) {
                accept(Token.DOT);
                varRefExpr = new SQLVariantRefExpr(lexer.stringVal(), true);
                lexer.nextToken();
            } else if (varRefExpr.getName().equalsIgnoreCase("@@session")) {
                accept(Token.DOT);
                varRefExpr = new SQLVariantRefExpr(lexer.stringVal(), false, true);
                lexer.nextToken();
            } else if (varRefExpr.getName().equals("@") && lexer.token() == Token.LITERAL_CHARS) {
                varRefExpr.setName("@'" + lexer.stringVal() + "'");
                lexer.nextToken();
            } else if (varRefExpr.getName().equals("@@") && lexer.token() == Token.LITERAL_CHARS) {
                varRefExpr.setName("@@'" + lexer.stringVal() + "'");
                lexer.nextToken();
            }
            return primaryRest(varRefExpr);
        case VALUES:
            lexer.nextToken();

            if (lexer.token() != Token.LPAREN) {
                SQLExpr expr = primary();
                SQLValuesQuery values = new SQLValuesQuery();
                values.addValue(new SQLListExpr(expr));
                return new SQLQueryExpr(new SQLSelect(values));
            }
            return this.methodRest(new SQLIdentifierExpr("VALUES"), true);
        case BINARY:
            lexer.nextToken();
            if (lexer.token() == Token.COMMA || lexer.token() == Token.SEMI || lexer.token() == Token.EOF) {
                return new SQLIdentifierExpr("BINARY");
            } else {
                SQLUnaryExpr binaryExpr = new SQLUnaryExpr(SQLUnaryOperator.BINARY, primary());
                return primaryRest(binaryExpr);
            }
        default:
            return super.primary();
        }

    }

    public final SQLExpr primaryRest(SQLExpr expr) {
        if (expr == null) {
            throw new IllegalArgumentException("expr");
        }

        if (lexer.token() == Token.LITERAL_CHARS) {
            if (expr instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr identExpr = (SQLIdentifierExpr) expr;
                String ident = identExpr.getName();

                if (ident.equalsIgnoreCase("x")) {
                    char ch = lexer.charAt(lexer.pos());
                    if (ch == '\'') {
                        String charValue = lexer.stringVal();
                        lexer.nextToken();
                        expr = new SQLHexExpr(charValue);
                        return primaryRest(expr);
                    }

//                } else if (ident.equalsIgnoreCase("b")) {
//                    String charValue = lexer.stringVal();
//                    lexer.nextToken();
//                    expr = new SQLBinaryExpr(charValue);
//
//                    return primaryRest(expr);
                } else if (ident.startsWith("_")) {
                    String charValue = lexer.stringVal();
                    lexer.nextToken();

                    MySqlCharExpr mysqlCharExpr = new MySqlCharExpr(charValue);
                    mysqlCharExpr.setCharset(identExpr.getName());
                    if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                        lexer.nextToken();

                        String collate = lexer.stringVal();
                        mysqlCharExpr.setCollate(collate);
                        if (lexer.token() == Token.LITERAL_CHARS) {
                            lexer.nextToken();
                        } else {
                            accept(Token.IDENTIFIER);
                        }
                    }

                    expr = mysqlCharExpr;

                    return primaryRest(expr);
                }
            } else if (expr instanceof SQLCharExpr) {
                String text2 = ((SQLCharExpr) expr).getText();
                do {
                    String chars = lexer.stringVal();
                    text2 += chars;
                    lexer.nextToken();
                } while (lexer.token() == Token.LITERAL_CHARS || lexer.token() == Token.LITERAL_ALIAS);
                expr = new SQLCharExpr(text2);
            } else if (expr instanceof SQLVariantRefExpr) {
                SQLMethodInvokeExpr concat = new SQLMethodInvokeExpr("CONCAT");
                concat.addArgument(expr);
                concat.addArgument(this.primary());
                expr = concat;

                return primaryRest(expr);
            }
        } else if (lexer.token() == Token.IDENTIFIER) {
            if (expr instanceof SQLHexExpr) {
                if ("USING".equalsIgnoreCase(lexer.stringVal())) {
                    lexer.nextToken();
                    if (lexer.token() != Token.IDENTIFIER) {
                        throw new ParserException("syntax error, illegal hex. " + lexer.info());
                    }
                    String charSet = lexer.stringVal();
                    lexer.nextToken();
                    expr.getAttributes().put("USING", charSet);

                    return primaryRest(expr);
                }
            } else if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                lexer.nextToken();

                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }

                if (lexer.token() != Token.IDENTIFIER
                    && lexer.token() != Token.LITERAL_CHARS) {
                    throw new ParserException("syntax error. " + lexer.info());
                }

                String collate = lexer.stringVal();
                lexer.nextToken();

                SQLBinaryOpExpr binaryExpr = new SQLBinaryOpExpr(expr, SQLBinaryOperator.COLLATE,
                    new SQLIdentifierExpr(collate), DbType.mysql);

                expr = binaryExpr;

                return primaryRest(expr);
            } else if (expr instanceof SQLVariantRefExpr) {
                if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                    lexer.nextToken();

                    if (lexer.token() != Token.IDENTIFIER
                        && lexer.token() != Token.LITERAL_CHARS) {
                        throw new ParserException("syntax error. " + lexer.info());
                    }

                    String collate = lexer.stringVal();
                    lexer.nextToken();

                    expr.putAttribute("COLLATE", collate);

                    return primaryRest(expr);
                }
            }
        } else if (lexer.token() == Token.LBRACKET) {
            SQLArrayExpr array = new SQLArrayExpr();
            array.setExpr(expr);
            lexer.nextToken();
            this.exprList(array.getValues(), array);
            accept(Token.RBRACKET);
            return primaryRest(array);
        }

//        if (lexer.token() == Token.LPAREN && expr instanceof SQLIdentifierExpr) {
//            SQLIdentifierExpr identExpr = (SQLIdentifierExpr) expr;
//            String ident = identExpr.getName();
//
//            if ("POSITION".equalsIgnoreCase(ident)) {
//                return parsePosition();
//            }
//        }

        if (lexer.token() == Token.VARIANT) {
            String variant = lexer.stringVal();
            if ("@".equals(variant)) {
                return userNameRest(expr);
            } else if ("@localhost".equals(variant)) {
                return userNameRest(expr);
            } else if ("@`%`".equals(variant)) {
                return userNameRest(expr);
            } else if (Pattern.matches("@`(.*)`", variant)) {
                /* MySqlLexer.java parser `root`@`127.0.0.1` to `root` and @`127.0.0.1` respectively,
                    however @`127.0.0.1` isn't identified here before, therefore we use a Regex to handle it */
                return userNameRest(expr);
            } else {
                throw new ParserException("syntax error. " + lexer.info());
            }
        }

        if (lexer.token() == Token.ERROR) {
            throw new ParserException("syntax error. " + lexer.info());
        }

        return super.primaryRest(expr);
    }

    public SQLName userName() {
        SQLName name = this.name();
        if (lexer.token() == Token.LPAREN && name.hashCode64() == FnvHash.Constants.CURRENT_USER) {
            lexer.nextToken();
            accept(Token.RPAREN);
            return name;
        }

        return (SQLName) userNameRest(name);
    }

    private SQLExpr userNameRest(SQLExpr expr) {
        if (lexer.token() != Token.VARIANT || !lexer.stringVal().startsWith("@")) {
            return expr;
        }

        MySqlUserName userName = new MySqlUserName();
        if (expr instanceof SQLCharExpr) {
            userName.setUserName(SQLUtils.normalizeNoTrim(((SQLCharExpr) expr).getText()));
        } else {
            userName.setUserName(SQLUtils.normalizeNoTrim(((SQLIdentifierExpr) expr).getName()));
        }

        String strVal = lexer.stringVal();
        lexer.nextToken();

        if (strVal.length() > 1) {
            userName.setHost(SQLUtils.normalizeNoTrim(strVal.substring(1)));
            return userName;
        }

        if (lexer.token() == Token.LITERAL_CHARS) {
            userName.setHost(SQLUtils.normalizeNoTrim(lexer.stringVal()));
        } else {
            if (lexer.token() == Token.PERCENT) {
                throw new ParserException("syntax error. " + lexer.info());
            } else {
                userName.setHost(SQLUtils.normalizeNoTrim(lexer.stringVal()));
            }
        }
        lexer.nextToken();

        if (lexer.identifierEquals(FnvHash.Constants.IDENTIFIED)) {
            Lexer.SavePoint mark = lexer.mark();

            lexer.nextToken();
            if (lexer.token() == Token.BY) {
                lexer.nextToken();
                if (lexer.identifierEquals(FnvHash.Constants.PASSWORD)) {
                    lexer.reset(mark);
                } else {
                    userName.setIdentifiedBy(SQLUtils.normalizeNoTrim(lexer.stringVal()));
                    lexer.nextToken();
                }
            } else {
                lexer.reset(mark);
            }
        }

        return userName;
    }

    protected SQLExpr parsePosition() {
        SQLExpr expr = this.primary();
        expr = this.primaryRest(expr);
        expr = bitXorRest(expr);
        expr = additiveRest(expr);
        expr = shiftRest(expr);
        expr = bitAndRest(expr);
        expr = bitOrRest(expr);

        if (lexer.token() == Token.IN) {
            accept(Token.IN);
        } else if (lexer.token() == Token.COMMA) {
            accept(Token.COMMA);
        } else {
            throw new ParserException("syntax error. " + lexer.info());
        }
        SQLExpr str = this.expr();
        accept(Token.RPAREN);

        SQLMethodInvokeExpr locate = new SQLMethodInvokeExpr("LOCATE");
        locate.addArgument(expr);
        locate.addArgument(str);

        return primaryRest(locate);
    }

    protected SQLExpr parseExtract() {
        SQLExpr expr;
        if (lexer.token() != Token.IDENTIFIER) {
            throw new ParserException("syntax error. " + lexer.info());
        }

        String unitVal = lexer.stringVal();
        SQLIntervalUnit unit = SQLIntervalUnit.valueOf(unitVal.toUpperCase());
        lexer.nextToken();

        accept(Token.FROM);

        SQLExpr value = expr();

        SQLExtractExpr extract = new SQLExtractExpr();
        extract.setValue(value);
        extract.setUnit(unit);
        accept(Token.RPAREN);

        expr = extract;

        return primaryRest(expr);
    }

    public SQLSelectParser createSelectParser() {
        return new MySqlSelectParser(this);
    }

    protected SQLExpr parseInterval() {
        accept(Token.INTERVAL);

        if (lexer.token() == Token.LPAREN) {
            lexer.nextToken();

            SQLMethodInvokeExpr methodInvokeExpr = new SQLMethodInvokeExpr("INTERVAL");
            if (lexer.token() != Token.RPAREN) {
                exprList(methodInvokeExpr.getArguments(), methodInvokeExpr);
            }

            accept(Token.RPAREN);

            // 

            if (methodInvokeExpr.getArguments().size() == 1 //
                && lexer.token() == Token.IDENTIFIER) {
                SQLExpr value = methodInvokeExpr.getArguments().get(0);
                String unit = lexer.stringVal();
                lexer.nextToken();

                SQLIntervalExpr intervalExpr = new SQLIntervalExpr();
                intervalExpr.setValue(value);
                intervalExpr.setUnit(SQLIntervalUnit.valueOf(unit.toUpperCase()));
                return intervalExpr;
            } else {
                return primaryRest(methodInvokeExpr);
            }
        } else {
            SQLExpr value = expr();

            if (lexer.token() != Token.IDENTIFIER) {
                throw new ParserException("Syntax error. " + lexer.info());
            }

            SQLIntervalUnit intervalUnit = null;

            String unit = lexer.stringVal();
            long unitHash = lexer.hash_lower();
            lexer.nextToken();

            intervalUnit = SQLIntervalUnit.valueOf(unit.toUpperCase());
            if (lexer.token() == Token.TO) {
                lexer.nextToken();
                if (unitHash == FnvHash.Constants.YEAR) {
                    if (lexer.identifierEquals(FnvHash.Constants.MONTH)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.YEAR_MONTH;
                    } else {
                        throw new ParserException("Syntax error. " + lexer.info());
                    }
                } else if (unitHash == FnvHash.Constants.DAY) {
                    if (lexer.identifierEquals(FnvHash.Constants.HOUR)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.DAY_HOUR;
                    } else if (lexer.identifierEquals(FnvHash.Constants.MINUTE)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.DAY_MINUTE;
                    } else if (lexer.identifierEquals(FnvHash.Constants.SECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.DAY_SECOND;
                    } else if (lexer.identifierEquals(FnvHash.Constants.MICROSECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.DAY_MICROSECOND;
                    } else {
                        throw new ParserException("Syntax error. " + lexer.info());
                    }
                } else if (unitHash == FnvHash.Constants.HOUR) {
                    if (lexer.identifierEquals(FnvHash.Constants.MINUTE)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.HOUR_MINUTE;
                    } else if (lexer.identifierEquals(FnvHash.Constants.SECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.HOUR_SECOND;
                    } else if (lexer.identifierEquals(FnvHash.Constants.MICROSECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.HOUR_MICROSECOND;
                    } else {
                        throw new ParserException("Syntax error. " + lexer.info());
                    }
                } else if (unitHash == FnvHash.Constants.MINUTE) {
                    if (lexer.identifierEquals(FnvHash.Constants.SECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.MINUTE_SECOND;
                    } else if (lexer.identifierEquals(FnvHash.Constants.MICROSECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.MINUTE_MICROSECOND;
                    } else {
                        throw new ParserException("Syntax error. " + lexer.info());
                    }
                } else if (unitHash == FnvHash.Constants.SECOND) {
                    if (lexer.identifierEquals(FnvHash.Constants.MICROSECOND)) {
                        lexer.nextToken();
                        intervalUnit = SQLIntervalUnit.SECOND_MICROSECOND;
                    } else {
                        throw new ParserException("Syntax error. " + lexer.info());
                    }
                } else {
                    throw new ParserException("Syntax error. " + lexer.info());
                }
            }

            SQLIntervalExpr intervalExpr = new SQLIntervalExpr();
            intervalExpr.setValue(value);
            intervalExpr.setUnit(intervalUnit);

            return intervalExpr;
        }
    }

    public SQLColumnDefinition parseColumn() {
        return parseColumn(false);
    }

    public SQLColumnDefinition parseColumn(boolean withoutName) {
        SQLColumnDefinition column = new SQLColumnDefinition();
        column.setDbType(dbType);
        SQLName name;
        if (!withoutName) {
            name = name();
        } else {
            SQLIdentifierExpr identifierExpr = new SQLIdentifierExpr("", 0);
            name = identifierExpr;
        }
        column.setName(name);
        column.setDataType(
            parseDataType());

        if (column.getDataType() != null && column.getDataType().jdbcType() == Types.CHAR) {
            // ENUM or SET with character set.
            if (lexer.identifierEquals(FnvHash.Constants.CHARACTER)) {
                lexer.nextToken();

                accept(Token.SET);

                if (lexer.token() != Token.IDENTIFIER
                    && lexer.token() != Token.LITERAL_CHARS) {
                    throw new ParserException(lexer.info());
                }
                column.setCharsetExpr(primary());
            }
        }

        // May multiple collate caused by type with collate.
        while (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
            lexer.nextToken();
            SQLExpr collateExpr;
            if (lexer.token() == Token.IDENTIFIER) {
                collateExpr = new SQLIdentifierExpr(lexer.stringVal());
            } else {
                collateExpr = new SQLCharExpr(lexer.stringVal());
            }
            lexer.nextToken();
            column.setCollateExpr(collateExpr);
        }

        if (lexer.identifierEquals(FnvHash.Constants.GENERATED)) {
            lexer.nextToken();
            acceptIdentifier("ALWAYS");
            accept(Token.AS);
            accept(Token.LPAREN);
            SQLExpr expr = this.expr();
            accept(Token.RPAREN);
            column.setGeneratedAlawsAs(expr);
        }

        return parseColumnRest(column);
    }

    public SQLColumnDefinition parseColumnRest(SQLColumnDefinition column) {
        if (lexer.token() == Token.ON) {
            lexer.nextToken();
            accept(Token.UPDATE);
            SQLExpr expr = this.primary();
            column.setOnUpdate(expr);
        }

        if (lexer.identifierEquals(FnvHash.Constants.ENCODE)) {
            lexer.nextToken();
            accept(Token.EQ);
            column.setEncode(this.charExpr());
        }
        if (lexer.identifierEquals(FnvHash.Constants.COMPRESSION)) {
            lexer.nextToken();
            accept(Token.EQ);
            column.setCompression(this.charExpr());
        }

        if (lexer.identifierEquals(FnvHash.Constants.CHARACTER)
            || lexer.identifierEquals(FnvHash.Constants.CHARSET)) {
            if (lexer.identifierEquals(FnvHash.Constants.CHARACTER)) {
                lexer.nextToken();
                accept(Token.SET);
            } else {
                lexer.nextToken();
            }

            SQLExpr charSet;
            if (lexer.token() == Token.IDENTIFIER) {
                charSet = new SQLIdentifierExpr(lexer.stringVal());
            } else {
                charSet = new SQLCharExpr(lexer.stringVal());
            }
            lexer.nextToken();
            column.setCharsetExpr(charSet);

            return parseColumnRest(column);
        }
        if (lexer.identifierEquals("disableindex")) {
            lexer.nextToken();
            if (lexer.token() == Token.TRUE) {
                lexer.nextToken();
                column.setDisableIndex(true);
            }
            return parseColumnRest(column);
        }
        if (lexer.identifierEquals("jsonIndexAttrs")) {
            lexer.nextToken();
            column.setJsonIndexAttrsExpr(new SQLIdentifierExpr(lexer.stringVal()));
            lexer.nextToken();
            return parseColumnRest(column);
        }
        if (lexer.identifierEquals("precision")) {
            lexer.nextToken();
            int precision = parseIntValue();
            acceptIdentifier("scale");
            int scale = parseIntValue();

            List<SQLExpr> arguments = column.getDataType().getArguments();
            arguments.add(new SQLIntegerExpr(precision));
            arguments.add(new SQLIntegerExpr(scale));

            return parseColumnRest(column);
        }
        if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
            lexer.nextToken();
            SQLExpr collateExpr;
            if (lexer.token() == Token.IDENTIFIER) {
                collateExpr = new SQLIdentifierExpr(lexer.stringVal());
            } else {
                collateExpr = new SQLCharExpr(lexer.stringVal());
            }
            lexer.nextToken();
            column.setCollateExpr(collateExpr);
            return parseColumnRest(column);
        }

        if (lexer.identifierEquals(FnvHash.Constants.PRECISION)
            && column.getDataType().nameHashCode64() == FnvHash.Constants.DOUBLE) {
            lexer.nextToken();
        }

        /* Allow partition in alter table.
        if (lexer.token() == Token.PARTITION) {
            throw new ParserException("syntax error " + lexer.info());
        }
        */

        if (lexer.identifierEquals("COLUMN_FORMAT")) {
            lexer.nextToken();
            SQLExpr expr = expr();
            column.setFormat(expr);
        }

        if (lexer.identifierEquals(FnvHash.Constants.STORAGE)) {
            lexer.nextToken();
            SQLExpr expr = expr();
            column.setStorage(expr);
        }

        if (lexer.token() == Token.AS) {
            lexer.nextToken();
            accept(Token.LPAREN);
            SQLExpr expr = expr();
            // qianjing: modify here to use generated always as since it's same in mysql
            column.setGeneratedAlawsAs(expr);
            accept(Token.RPAREN);
        }

        if (lexer.identifierEquals(FnvHash.Constants.STORED) || lexer.identifierEquals("PERSISTENT")) {
            lexer.nextToken();
            column.setStored(true);
        }

        if (lexer.identifierEquals(FnvHash.Constants.VIRTUAL)) {
            lexer.nextToken();
            column.setVirtual(true);
        }

        if (lexer.identifierEquals(FnvHash.Constants.LOGICAL)) {
            lexer.nextToken();
            column.setLogical(true);
        }

        if (lexer.identifierEquals(FnvHash.Constants.DELIMITER)) {
            lexer.nextToken();
            SQLExpr expr = this.expr();
            column.setDelimiter(expr);
            return parseColumnRest(column);
        }
        if (lexer.identifierEquals("delimiter_tokenizer")) {
            lexer.nextToken();
            SQLExpr expr = this.expr();
            column.setDelimiterTokenizer(expr);
            return parseColumnRest(column);
        }

        if (lexer.identifierEquals("nlp_tokenizer")) {
            lexer.nextToken();
            SQLExpr expr = this.expr();
            column.setNlpTokenizer(expr);
        }

        if (lexer.identifierEquals("value_type")) {
            lexer.nextToken();
            SQLExpr expr = this.expr();
            column.setValueType(expr);
        }

        if (lexer.identifierEquals(FnvHash.Constants.COLPROPERTIES)) {
            lexer.nextToken();
            this.parseAssignItem(column.getColProperties(), column);
        }

        if (lexer.identifierEquals(FnvHash.Constants.ANNINDEX)) {
            lexer.nextToken();

            accept(Token.LPAREN);
            SQLAnnIndex annIndex = new SQLAnnIndex();
            for (; ; ) {
                if (lexer.identifierEquals(FnvHash.Constants.TYPE)) {
                    lexer.nextToken();
                    accept(Token.EQ);
                    String type = lexer.stringVal();
                    annIndex.setIndexType(type);
                    accept(Token.LITERAL_CHARS);
                } else if (lexer.identifierEquals(FnvHash.Constants.RTTYPE)) {
                    lexer.nextToken();
                    accept(Token.EQ);
                    String type = lexer.stringVal();
                    annIndex.setRtIndexType(type);
                    accept(Token.LITERAL_CHARS);
                } else if (lexer.identifierEquals(FnvHash.Constants.DISTANCE)) {
                    lexer.nextToken();
                    accept(Token.EQ);
                    String type = lexer.stringVal();
                    annIndex.setDistance(type);
                    accept(Token.LITERAL_CHARS);
                }

                if (lexer.token() == Token.COMMA) {
                    lexer.nextToken();
                    continue;
                }

                break;
            }

            accept(Token.RPAREN);

            column.setAnnIndex(annIndex);

            return parseColumnRest(column);
        }

        if (Token.COLUMN == this.lexer.token()) {
            this.lexer.nextToken();
            this.acceptIdentifier("SECURED");
            accept(Token.WITH);
            column.setSecuredWith(new SQLIdentifierExpr(this.lexer.stringVal()));
            this.lexer.nextToken();
        }

        super.parseColumnRest(column);

        return column;
    }

    protected SQLDataType parseDataTypeRest(SQLDataType dataType) {
        super.parseDataTypeRest(dataType);

        for (; ; ) {
            if (lexer.identifierEquals(FnvHash.Constants.UNSIGNED)) {
                lexer.nextToken();
                ((SQLDataTypeImpl) dataType).setUnsigned(true);
            } else if (lexer.identifierEquals(FnvHash.Constants.SIGNED)) {
                lexer.nextToken();
                ((SQLDataTypeImpl) dataType).setUnsigned(false);
            } else if (lexer.identifierEquals(FnvHash.Constants.ZEROFILL)) {
                lexer.nextToken();
                ((SQLDataTypeImpl) dataType).setZerofill(true);
            } else {
                break;
            }
        }

        return dataType;
    }

    public SQLAssignItem parseAssignItem(boolean variant) {
        SQLAssignItem item = new SQLAssignItem();

        SQLExpr var = primary();

        String ident = null;
        long identHash = 0;
        if (variant && var instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr identExpr = (SQLIdentifierExpr) var;
            ident = identExpr.getName();
            identHash = identExpr.hashCode64();

            if (identHash == FnvHash.Constants.GLOBAL) {
                ident = lexer.stringVal();
                lexer.nextToken();
                var = new SQLVariantRefExpr(ident, true);
            } else if (identHash == FnvHash.Constants.SESSION) {
                ident = lexer.stringVal();
                lexer.nextToken();
                var = new SQLVariantRefExpr(ident, false, true);
            } else {
                var = new SQLVariantRefExpr(ident);
            }
        }

        if (identHash == FnvHash.Constants.NAMES) {
            String charset = lexer.stringVal();

            SQLExpr varExpr = null;
            boolean chars = false;
            final Token token = lexer.token();
            if (token == Token.IDENTIFIER) {
                lexer.nextToken();
            } else if (token == Token.DEFAULT) {
                charset = "DEFAULT";
                lexer.nextToken();
            } else if (token == Token.QUES) {
                varExpr = new SQLVariantRefExpr("?");
                lexer.nextToken();
            } else {
                chars = true;
                accept(Token.LITERAL_CHARS);
            }

            if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                MySqlCharExpr charsetExpr = new MySqlCharExpr(charset);
                lexer.nextToken();

                String collate = lexer.stringVal();
                lexer.nextToken();
                charsetExpr.setCollate(collate);

                item.setValue(charsetExpr);
            } else {
                if (varExpr != null) {
                    item.setValue(varExpr);
                } else {
                    item.setValue(chars
                        ? new SQLCharExpr(charset)
                        : new SQLIdentifierExpr(charset)
                    );
                }
            }

            item.setTarget(var);
            return item;
        } else if (identHash == FnvHash.Constants.CHARACTER) {
            var = new SQLVariantRefExpr("CHARACTER SET");
            accept(Token.SET);
            if (lexer.token() == Token.EQ) {
                lexer.nextToken();
            }
        } else if (identHash == FnvHash.Constants.CHARSET) {
            var = new SQLVariantRefExpr("CHARACTER SET");
            if (lexer.token() == Token.EQ) {
                lexer.nextToken();
            }
        } else if (identHash == FnvHash.Constants.TRANSACTION) {
            var = new SQLVariantRefExpr("TRANSACTION");
            if (lexer.token() == Token.EQ) {
                lexer.nextToken();
            }
        } else {
            if (lexer.token() == Token.COLONEQ) {
                lexer.nextToken();
            } else {
                accept(Token.EQ);
            }
        }

        if (lexer.token() == Token.ON) {
            lexer.nextToken();
            item.setValue(new SQLIdentifierExpr("ON"));
        } else {
            item.setValue(this.expr());
        }

        item.setTarget(var);
        return item;
    }

    public SQLName nameRest(SQLName name) {
        if (lexer.token() == Token.VARIANT && "@".equals(lexer.stringVal())) {
            lexer.nextToken();
            MySqlUserName userName = new MySqlUserName();
            userName.setUserName(((SQLIdentifierExpr) name).getName());

            if (lexer.token() == Token.LITERAL_CHARS) {
                userName.setHost(lexer.stringVal());
            } else if (lexer.token() == Token.LITERAL_ALIAS) {
                userName.setHost(lexer.stringVal().substring(1, lexer.stringVal().length() - 1));
            } else if (lexer.token() == Token.PERCENT) {
                userName.setHost(Token.PERCENT.name);
            } else {
                userName.setHost(lexer.stringVal());
            }
            lexer.nextToken();

            if (lexer.identifierEquals(FnvHash.Constants.IDENTIFIED)) {
                lexer.nextToken();
                accept(Token.BY);
                userName.setIdentifiedBy(lexer.stringVal());
                lexer.nextToken();
            }

            return userName;
        }
        return super.nameRest(name);
    }

    @Override
    public MySqlPrimaryKey parsePrimaryKey() {
        MySqlPrimaryKey primaryKey = new MySqlPrimaryKey();
        parseIndex(primaryKey.getIndexDefinition());
        return primaryKey;
        /*
        accept(Token.PRIMARY);
        accept(Token.KEY);

        MySqlPrimaryKey primaryKey = new MySqlPrimaryKey();

        if (lexer.identifierEquals(FnvHash.Constants.USING)) {
            lexer.nextToken();
            primaryKey.setIndexType(lexer.stringVal());
            lexer.nextToken();
        }

        if (lexer.token() != Token.LPAREN) {
            SQLName name = this.name();
            primaryKey.setName(name);
        }

        accept(Token.LPAREN);
        for (;;) {
            setAllowIdentifierMethod(false);

            SQLExpr expr;
            if (lexer.token() == Token.LITERAL_ALIAS) {
                expr = this.name();
            } else {
                expr = this.expr();
            }

            setAllowIdentifierMethod(true);

            SQLSelectOrderByItem item = new SQLSelectOrderByItem();

            item.setExpr(expr);

            if (lexer.token() == Token.ASC) {
                lexer.nextToken();
                item.setType(SQLOrderingSpecification.ASC);
            } else if (lexer.token() == Token.DESC) {
                lexer.nextToken();
                item.setType(SQLOrderingSpecification.DESC);
            }

            primaryKey.addColumn(item);
            if (!(lexer.token() == (Token.COMMA))) {
                break;
            } else {
                lexer.nextToken();
            }
        }
        accept(Token.RPAREN);

        for (;;) {
            if (lexer.token() == Token.COMMENT) {
                lexer.nextToken();
                SQLExpr comment = this.primary();
                primaryKey.setComment(comment);
            } else if (lexer.identifierEquals(FnvHash.Constants.KEY_BLOCK_SIZE)) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr keyBlockSize = this.primary();
                primaryKey.setKeyBlockSize(keyBlockSize);
            } else if (lexer.identifierEquals(FnvHash.Constants.USING)) {
                lexer.nextToken();
                primaryKey.setIndexType(lexer.stringVal());
                accept(Token.IDENTIFIER);
            } else {
                break;
            }
        }

        return primaryKey;
        */
    }

    public MySqlUnique parseUnique() {
        MySqlUnique unique = new MySqlUnique();
        parseIndex(unique.getIndexDefinition());
        return unique;
        /*
        accept(Token.UNIQUE);

        if (lexer.token() == Token.KEY) {
            lexer.nextToken();
        }

        if (lexer.token() == Token.INDEX) {
            lexer.nextToken();
        }

        MySqlUnique unique = new MySqlUnique();

        if (lexer.token() != Token.LPAREN && !lexer.identifierEquals(FnvHash.Constants.USING)) {
            SQLName indexName = name();
            unique.setName(indexName);
        }

        //5.5语法 USING BTREE 放在index 名字后
        if (lexer.identifierEquals(FnvHash.Constants.USING)) {
            lexer.nextToken();
            unique.setIndexType(lexer.stringVal());
            lexer.nextToken();
        }

        parseIndexRest(unique);

        for (;;) {
            if (lexer.token() == Token.COMMENT) {
                lexer.nextToken();
                SQLExpr comment = this.primary();
                unique.setComment(comment);
            } else if (lexer.identifierEquals(FnvHash.Constants.KEY_BLOCK_SIZE)) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr keyBlockSize = this.primary();
                unique.setKeyBlockSize(keyBlockSize);
            } else if (lexer.identifierEquals(FnvHash.Constants.USING)) {
                lexer.nextToken();
                unique.setIndexType(lexer.stringVal());
                accept(Token.IDENTIFIER);
            } else {
                break;
            }
        }

        return unique;
        */
    }

    public MysqlForeignKey parseForeignKey() {
        accept(Token.FOREIGN);
        accept(Token.KEY);

        MysqlForeignKey fk = new MysqlForeignKey();

        if (lexer.token() != Token.LPAREN) {
            SQLName indexName = name();
            fk.setIndexName(indexName);
        }

        accept(Token.LPAREN);
        this.names(fk.getReferencingColumns(), fk);
        accept(Token.RPAREN);

        accept(Token.REFERENCES);

        fk.setReferencedTableName(this.name());

        accept(Token.LPAREN);
        this.names(fk.getReferencedColumns());
        accept(Token.RPAREN);

        if (lexer.identifierEquals(FnvHash.Constants.MATCH)) {
            // Do not support match full/partial/simple currently;
            throw new ParserException("Only Support MATCH SIMPLE for now, " + lexer.info());
//            lexer.nextToken();
//            if (lexer.identifierEquals("FULL") || lexer.token() == Token.FULL) {
//                fk.setReferenceMatch(Match.FULL);
//                lexer.nextToken();
//            } else if (lexer.identifierEquals(FnvHash.Constants.PARTIAL)) {
//                fk.setReferenceMatch(Match.PARTIAL);
//                lexer.nextToken();
//            } else if (lexer.identifierEquals(FnvHash.Constants.SIMPLE)) {
//                fk.setReferenceMatch(Match.SIMPLE);
//                lexer.nextToken();
//            } else {
//                throw new ParserException("TODO : " + lexer.info());
//            }
        }

        int update = 0;
        int delete = 0;

        while (lexer.token() == Token.ON) {
            lexer.nextToken();

            if (lexer.token() == Token.DELETE) {
                delete++;
                if (delete > 1) {
                    throw new ParserException("syntax error, multiple DELETE near " + lexer.info());
                }
                lexer.nextToken();

                Option option = parseReferenceOption();
                fk.setOnDelete(option);
            } else if (lexer.token() == Token.UPDATE) {
                update++;
                if (update > 1) {
                    throw new ParserException("syntax error, multiple UPDATE near " + lexer.info());
                }
                lexer.nextToken();

                Option option = parseReferenceOption();
                fk.setOnUpdate(option);
            } else {
                throw new ParserException("syntax error, expect DELETE or UPDATE, actual " + lexer.token() + " "
                    + lexer.info());
            }
        }
        return fk;
    }

    protected SQLAggregateExpr parseAggregateExprRest(SQLAggregateExpr aggregateExpr) {
        if (lexer.token() == Token.ORDER) {
            SQLOrderBy orderBy = this.parseOrderBy();
            aggregateExpr.setOrderBy(orderBy);
            //为了兼容之前的逻辑
            aggregateExpr.putAttribute("ORDER BY", orderBy);
        }
        if (lexer.identifierEquals(FnvHash.Constants.SEPARATOR)) {
            lexer.nextToken();

            SQLExpr seperator = this.primary();
            seperator.setParent(aggregateExpr);

            aggregateExpr.putAttribute("SEPARATOR", seperator);
        }
        return aggregateExpr;
    }

    public MySqlOrderingExpr parseSelectGroupByItem() {
        MySqlOrderingExpr item = new MySqlOrderingExpr();

        item.setExpr(expr());

        if (lexer.token() == Token.ASC) {
            lexer.nextToken();
            item.setType(SQLOrderingSpecification.ASC);
        } else if (lexer.token() == Token.DESC) {
            lexer.nextToken();
            item.setType(SQLOrderingSpecification.DESC);
        }

        return item;
    }

    public void parseSubPartitions(SQLPartition partitionDef) {
        for (; ; ) {
            acceptIdentifier("SUBPARTITION");
            SQLSubPartition subPartition = parseSubPartition();
            partitionDef.addSubPartition(subPartition);
            if (lexer.token() == Token.COMMA) {
                lexer.nextToken();
                continue;
            }
            break;
        }
    }

    public SQLSubPartition parseSubPartition() {
        SQLSubPartition subPartition = new SQLSubPartition();
        subPartition.setName(this.name());
        SQLPartitionValue values = this.parsePartitionValues();
        if (values != null) {
            subPartition.setValues(values);
        }
        for (; ; ) {
            boolean storage = false;
            if (lexer.identifierEquals(FnvHash.Constants.DATA)) {
                lexer.nextToken();
                acceptIdentifier("DIRECTORY");
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                subPartition.setDataDirectory(this.expr());
            } else if (lexer.token() == Token.TABLESPACE) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLName tableSpace = this.name();
                subPartition.setTablespace(tableSpace);
            } else if (lexer.token() == Token.INDEX) {
                lexer.nextToken();
                acceptIdentifier("DIRECTORY");
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                subPartition.setIndexDirectory(this.expr());
            } else if (lexer.identifierEquals(FnvHash.Constants.MAX_ROWS)) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr maxRows = this.primary();
                subPartition.setMaxRows(maxRows);
            } else if (lexer.identifierEquals(FnvHash.Constants.MIN_ROWS)) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr minRows = this.primary();
                subPartition.setMaxRows(minRows);
            } else if (lexer.identifierEquals(FnvHash.Constants.ENGINE) || //
                (storage = (lexer.token() == Token.STORAGE || lexer.identifierEquals(FnvHash.Constants.STORAGE)))) {
                if (storage) {
                    lexer.nextToken();
                }
                acceptIdentifier("ENGINE");

                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }

                SQLName engine = this.name();
                subPartition.setEngine(engine);
            } else if (lexer.token() == Token.COMMENT) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr comment = this.primary();
                subPartition.setComment(comment);
            } else {
                break;
            }
        }

        return subPartition;
    }

    // parse one value of "part_name ADD|DROP VALUES()"
    // as one SQLAlterTableModifyPartitionValues with isSubPartition=false
    public SQLAlterTableModifyPartitionValues parseModifyPartitionValues(String parentPartName) {

        SQLPartition partition = new SQLPartition();
        partition.setName(this.name());

        SQLAlterTableModifyPartitionValues sqlAlterTableModifyPartitionValuesItem =
            new SQLAlterTableModifyPartitionValues(partition, false);
        if (lexer.identifierEquals(FnvHash.Constants.ADD)) {
            lexer.nextToken();
            //subPartition.setAddValues(true);
            sqlAlterTableModifyPartitionValuesItem.setAdd(true);
        } else {
            accept(Token.DROP);
            sqlAlterTableModifyPartitionValuesItem.setDrop(true);
        }

        SQLPartitionValue values = parseModifyValues();
        partition.setValues(values);

        if (lexer.identifierEquals("ALGORITHM")) {
            lexer.nextToken();
            accept(Token.EQ);
            String algorithm = lexer.stringVal();
            sqlAlterTableModifyPartitionValuesItem.setAlgorithm(algorithm);
            lexer.nextToken();
        }

        return sqlAlterTableModifyPartitionValuesItem;
    }

    // parse one value of "sub_part_name ADD|DROP VALUES()"
    // as one SQLAlterTableModifyPartitionValues with isSubPartition=true
    public SQLAlterTableModifyPartitionValues parseModifySubPartitionValues(SQLPartition parentPartition) {

        SQLSubPartition subPartition = new SQLSubPartition();
        subPartition.setName(this.name());

        SQLPartition parentPart = parentPartition;
        if (parentPart == null) {
            parentPart = new SQLPartition();
            parentPart.setName(null);
        }
        parentPart.addSubPartition(subPartition);

        SQLAlterTableModifyPartitionValues sqlAlterTableModifyPartitionValuesItem =
            new SQLAlterTableModifyPartitionValues(parentPart, true);
        if (lexer.identifierEquals(FnvHash.Constants.ADD)) {
            lexer.nextToken();
            //subPartition.setAddValues(true);
            sqlAlterTableModifyPartitionValuesItem.setAdd(true);
        } else {
            accept(Token.DROP);
            sqlAlterTableModifyPartitionValuesItem.setDrop(true);
        }

        SQLPartitionValue values = parseModifyValues();
        subPartition.setValues(values);
        if (lexer.identifierEquals("ALGORITHM")) {
            lexer.nextToken();
            accept(Token.EQ);
            String algorithm = lexer.stringVal();
            sqlAlterTableModifyPartitionValuesItem.setAlgorithm(algorithm);
            lexer.nextToken();
        }

        return sqlAlterTableModifyPartitionValuesItem;
    }

    public SQLPartitionValue parseModifyValues() {
        accept(Token.VALUES);

        SQLPartitionValue values = new SQLPartitionValue(SQLPartitionValue.Operator.List);

        accept(Token.LPAREN);

        this.exprList(values.getItems(), values);

        accept(Token.RPAREN);

        return values;
    }

    public SQLPartition parsePartition() {
        if (lexer.identifierEquals(FnvHash.Constants.DBPARTITION)
            || lexer.identifierEquals(FnvHash.Constants.TBPARTITION)
            || lexer.identifierEquals(FnvHash.Constants.SUBPARTITION)) {
            lexer.nextToken();
        } else {
            accept(Token.PARTITION);
        }

        SQLPartition partitionDef = new SQLPartition();

        SQLName name;
        if (lexer.token() == Token.LITERAL_INT) {
            Number number = lexer.integerValue();
            name = new SQLIdentifierExpr(number.toString());
            lexer.nextToken();
        } else {
            name = this.name();
        }
        partitionDef.setName(name);

        SQLPartitionValue values = this.parsePartitionValues();
        if (values != null) {
            partitionDef.setValues(values);
        }

        for (; ; ) {
            boolean storage = false;
            if (lexer.identifierEquals(FnvHash.Constants.DATA)) {
                lexer.nextToken();
                acceptIdentifier("DIRECTORY");
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                partitionDef.setDataDirectory(this.expr());
            } else if (lexer.token() == Token.TABLESPACE) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLName tableSpace = this.name();
                partitionDef.setTablespace(tableSpace);
            } else if (lexer.token() == Token.INDEX) {
                lexer.nextToken();
                acceptIdentifier("DIRECTORY");
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                partitionDef.setIndexDirectory(this.expr());
            } else if (lexer.identifierEquals(FnvHash.Constants.MAX_ROWS)) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr maxRows = this.primary();
                partitionDef.setMaxRows(maxRows);
            } else if (lexer.identifierEquals(FnvHash.Constants.MIN_ROWS)) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr minRows = this.primary();
                partitionDef.setMaxRows(minRows);
            } else if (lexer.identifierEquals(FnvHash.Constants.ENGINE) || //
                (storage = (lexer.token() == Token.STORAGE || lexer.identifierEquals(FnvHash.Constants.STORAGE)))) {
                if (storage) {
                    lexer.nextToken();
                }
                acceptIdentifier("ENGINE");

                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }

                SQLName engine = this.name();
                partitionDef.setEngine(engine);
            } else if (lexer.token() == Token.COMMENT) {
                lexer.nextToken();
                if (lexer.token() == Token.EQ) {
                    lexer.nextToken();
                }
                SQLExpr comment = this.primary();
                partitionDef.setComment(comment);
            } else if (lexer.identifierEquals(FnvHash.Constants.LOCALITY)) {
                lexer.nextToken();
                acceptIf(Token.EQ);
                partitionDef.setLocality(this.name());
            } else {
                break;
            }
        }

        if (lexer.identifierEquals(FnvHash.Constants.SUBPARTITIONS)) {
            /**
             * parse for :
             *  partition p0 values less than ('2020-01-01','abc')
             *  ->SUBPARTITIONS cnt
             *  (
             * 	    subpartition sp1 values than (...),
             * 	    subpartition sp2 values than (...),
             * 	    ...
             * 	),...
             */
            lexer.nextToken();
            Number intValue = lexer.integerValue();
            SQLIntegerExpr numExpr = new SQLIntegerExpr(intValue);
            partitionDef.setSubPartitionsCount(numExpr);
            lexer.nextToken();
        }

        if (lexer.token() == Token.LPAREN) {
            /**
             *  handle the LPAREN of "partition p0 ... ()"
             */
            lexer.nextToken();

            /**
             * parse for :
             *  partition p0 values less than ('2020-01-01','abc')
             * ->(
             *
             * 	    subpartition sp1 values than (...),
             * 	    subpartition sp2 values than (...),
             * 	    ...
             * 	),...
             */
            if (lexer.identifierEquals("SUBPARTITION")) {
                parseSubPartitions(partitionDef);
            }

            /**
             *  handle the RPAREN of "partition p0 ... ()"
             */
            accept(Token.RPAREN);
        }
        return partitionDef;
    }

    protected SQLExpr parseAliasExpr(String alias) {
        if (isEnabled(SQLParserFeature.KeepNameQuotes)) {
            return new SQLIdentifierExpr(alias);
        }
        Lexer newLexer = new Lexer(ByteString.from(alias));
        newLexer.nextTokenValue();
        return new SQLCharExpr(newLexer.stringVal());
    }

    public boolean parseTableOptions(List<SQLAssignItem> assignItems, SQLDDLStatement parent) {
        // Check whether table options.
        boolean succeed = false;

        while (lexer.token() != Token.EOF && lexer.token() != Token.SEMI) {
            final long hash = lexer.hash_lower();
            final int idx = Arrays.binarySearch(SINGLE_WORD_TABLE_OPTIONS_CODES, hash);
            SQLAssignItem assignItem = null;
            Lexer.SavePoint mark = null;

            if (idx >= 0 && idx < SINGLE_WORD_TABLE_OPTIONS_CODES.length &&
                SINGLE_WORD_TABLE_OPTIONS_CODES[idx] == hash &&
                (lexer.token() == Token.IDENTIFIER || (lexer.token().name != null
                    && lexer.token().name.length() == SINGLE_WORD_TABLE_OPTIONS[idx].length()))) {
                // Special items.
                if (lexer.token() == Token.TABLESPACE) {
                    lexer.nextToken();

                    MySqlCreateTableStatement.TableSpaceOption option =
                        new MySqlCreateTableStatement.TableSpaceOption();
                    option.setName(name());

                    if (lexer.identifierEquals("STORAGE")) {
                        lexer.nextToken();
                        option.setStorage(name());
                    }
                    assignItem = new SQLAssignItem(new SQLIdentifierExpr("TABLESPACE"), option);
                } else if (lexer.token() == Token.UNION) {
                    lexer.nextToken();
                    if (lexer.token() == Token.EQ) {
                        lexer.nextToken();
                    }

                    accept(Token.LPAREN);
                    SQLListExpr list = new SQLListExpr();
                    exprList(list.getItems(), list);
                    accept(Token.RPAREN);
                    assignItem = new SQLAssignItem(new SQLIdentifierExpr("UNION"), list);
                } else if (lexer.identifierEquals("PACK_KEYS")) {
                    // Caution: Not in MySql documents.
                    lexer.nextToken();
                    if (lexer.token() == Token.EQ) {
                        lexer.nextToken();
                    }

                    if (lexer.identifierEquals("PACK")) {
                        lexer.nextToken();
                        accept(Token.ALL);
                        assignItem =
                            new SQLAssignItem(new SQLIdentifierExpr("PACK_KEYS"), new SQLIdentifierExpr("PACK ALL"));
                    } else {
                        assignItem = new SQLAssignItem(new SQLIdentifierExpr("PACK_KEYS"), expr());
                    }
                } else if (lexer.identifierEquals(FnvHash.Constants.ENGINE)) {
                    lexer.nextToken();
                    if (lexer.token() == Token.EQ) {
                        lexer.nextToken();
                    }

                    SQLExpr expr;
                    if (lexer.token() == Token.MERGE) {
                        expr = new SQLIdentifierExpr(lexer.stringVal());
                        lexer.nextToken();
                    } else {
                        expr = expr();
                    }
                    assignItem = new SQLAssignItem(new SQLIdentifierExpr("ENGINE"), expr);
                } else {
                    // Find single key, store as KV.
                    lexer.nextToken();
                    if (lexer.token() == Token.EQ) {
                        lexer.nextToken();
                    }

                    // STORAGE_POLICY
                    if (idx == 9) {
                        if (lexer.token() == Token.LITERAL_CHARS || lexer.token() == Token.LITERAL_ALIAS) {
                            String policy = StringUtils.removeNameQuotes(lexer.stringVal());
                            assignItem = new SQLAssignItem(new SQLIdentifierExpr(SINGLE_WORD_TABLE_OPTIONS[idx]),
                                new SQLCharExpr(policy));
                            lexer.nextToken();
                        } else {
                            throw new ParserException("syntax error. " + lexer.info());
                        }
                    } else {
                        assignItem = new SQLAssignItem(new SQLIdentifierExpr(SINGLE_WORD_TABLE_OPTIONS[idx]), expr());
                    }
                }
            } else {
                // Following may not table options. Save mark.
                mark = lexer.mark();

//                if (lexer.identifierEquals("LOCALITY")) {
//                    throw new ParserException("Unsupported usage: set table locality! " + lexer.info());
//                }
                if (lexer.token() == Token.DEFAULT) {
                    // [DEFAULT] CHARACTER SET [=] charset_name
                    // [DEFAULT] COLLATE [=] collation_name
                    lexer.nextToken();
                    if (lexer.identifierEquals(FnvHash.Constants.CHARSET)) {
                        lexer.nextToken();
                        if (lexer.token() == Token.EQ) {
                            lexer.nextToken();
                        }
                        assignItem = new SQLAssignItem(new SQLIdentifierExpr("CHARSET"), expr());
                    } else if (lexer.identifierEquals(FnvHash.Constants.CHARACTER)) {
                        lexer.nextToken();
                        accept(Token.SET);
                        if (lexer.token() == Token.EQ) {
                            lexer.nextToken();
                        }
                        assignItem = new SQLAssignItem(new SQLIdentifierExpr("CHARACTER SET"), expr());
                    } else if (lexer.identifierEquals(FnvHash.Constants.COLLATE)) {
                        lexer.nextToken();
                        if (lexer.token() == Token.EQ) {
                            lexer.nextToken();
                        }
                        assignItem = new SQLAssignItem(new SQLIdentifierExpr("COLLATE"), expr());
                    }
                } else if (lexer.identifierEquals(FnvHash.Constants.CHARACTER)) {
                    // CHARACTER SET [=] charset_name
                    lexer.nextToken();
                    accept(Token.SET);
                    if (lexer.token() == Token.EQ) {
                        lexer.nextToken();
                    }
                    assignItem = new SQLAssignItem(new SQLIdentifierExpr("CHARACTER SET"), expr());
                } else if (lexer.identifierEquals(FnvHash.Constants.CHARSET)) {
                    // CHARSET [=] charset_name
                    lexer.nextToken();
                    if (lexer.token() == Token.EQ) {
                        lexer.nextToken();
                    }
                    assignItem = new SQLAssignItem(new SQLIdentifierExpr("CHARSET"), expr());
                } else if (lexer.identifierEquals(FnvHash.Constants.DATA) ||
                    lexer.token() == Token.INDEX) {
                    // {DATA|INDEX} DIRECTORY [=] 'absolute path to directory'
                    lexer.nextToken();
                    if (lexer.identifierEquals("DIRECTORY")) {
                        lexer.nextToken();
                        if (lexer.token() == Token.EQ) {
                            lexer.nextToken();
                        }
                        assignItem = new SQLAssignItem(new SQLIdentifierExpr("COLLATE"), expr());
                    }
                } else if (lexer.identifierEquals(FnvHash.Constants.LOCALITY)) {
                    lexer.nextToken();
                    acceptIf(Token.EQ);
                    assignItem = new SQLAssignItem(new SQLIdentifierExpr("LOCALITY"), expr());
                }
            }

            if (assignItem != null) {
                assignItem.setParent(parent);
                assignItems.add(assignItem);
                succeed = true;
            } else {
                if (mark != null) {
                    lexer.reset(mark);
                }
                return succeed;
            }

            // Optional comma.
            if (lexer.token() == Token.COMMA) {
                lexer.nextToken();
            } else if (lexer.token() == Token.EOF) {
                break;
            }
        }
        return succeed;
    }
}
