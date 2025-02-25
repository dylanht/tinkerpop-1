/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.language.grammar;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;

/**
 * Generic Literal visitor test
 */
@RunWith(Enclosed.class)
public class GeneralLiteralVisitorTest {

    static Object parseGenericLiteralRange(final String query) {
        final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(query));
        final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
        final GremlinParser.GenericLiteralRangeContext ctx = parser.genericLiteralRange();
        return GenericLiteralVisitor.getInstance().visitGenericLiteralRange(ctx);
    }

    @RunWith(Parameterized.class)
    public static class ValidIntegerRangeTest {

        @Parameterized.Parameter(value = 0)
        public int start;

        @Parameterized.Parameter(value = 1)
        public int end;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {1, 1000000},
                    {1, 1},
                    {1000000, 1},
            });
        }

        @Test
        public void shouldParse() {
            assertThat(Math.abs(end - start), lessThan(GenericLiteralVisitor.TOTAL_INTEGER_RANGE_RESULT_COUNT_LIMIT));

            final Object result = parseGenericLiteralRange(String.format("%d..%d", start, end));
            assertThat(result, instanceOf(List.class));
            int expectedValue = start;
            final List<Object> results = (List<Object>) result;

            // iterate the result and check they are as expected
            for (Object actualValue : results) {
                assertThat(actualValue, instanceOf(Integer.class));
                assertEquals(expectedValue, actualValue);
                if (start <= end) {
                    expectedValue++;
                } else {
                    expectedValue--;
                }
            }
            assertEquals(Math.abs(end - start) + 1, results.size());
        }
    }

    @RunWith(Parameterized.class)
    public static class InvalidIntegerRangeTest {
        @Parameterized.Parameter(value = 0)
        public int start;

        @Parameterized.Parameter(value = 1)
        public int end;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {0, 10000000},
                    {10000000, 1},
            });
        }

        /**
         * test integer range exceed limit
         */
        @Test(expected = IllegalArgumentException.class)
        public void shouldNotParse() {
            assertThat(Math.abs(end - start), greaterThanOrEqualTo(GenericLiteralVisitor.TOTAL_INTEGER_RANGE_RESULT_COUNT_LIMIT));
            parseGenericLiteralRange(String.format("%d..%d", start, end));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidStringRangeTest {
        @Parameterized.Parameter(value = 0)
        public String start;

        @Parameterized.Parameter(value = 1)
        public String end;

        @Parameterized.Parameter(value = 2)
        public String expected;

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"'abc1'", "'abc3'", "abc1_abc2_abc3"},
                    {"\"abc1\"", "'abc3'", "abc1_abc2_abc3"},
                    {"\"abc1\"", "\"abc3\"", "abc1_abc2_abc3"},
                    {"'abc1'", "\"abc3\"", "abc1_abc2_abc3"},
                    {"'a11a'", "'a11d'", "a11a_a11b_a11c_a11d"},
                    {"'a11N'", "'a11L'", "a11N_a11M_a11L"},
                    {"'a113'", "'a111'", "a113_a112_a111"},
                    {"'a111'", "'a111'", "a111"},
                    {"'a'", "'c'", "a_b_c"},
                    {"'6'", "'3'", "6_5_4_3"},
                    {"'1'", "'1'", "1"},
                    {"'N'", "'N'", "N"},
                    {"''", "''", "_"},
            });
        }

        @Test
        public void shouldParse() {
            final Object result = parseGenericLiteralRange(String.format("%s..%s", start, end));
            assertThat(result, instanceOf(List.class));

            final List<Object> results = (List<Object>) result;
            if (expected.equals("_")) {
                // handle special case for empty range
                assertEquals(0, results.size());
                return;
            }

            final String[] expectedResults = expected.split("_");
            assertEquals(expectedResults.length, results.size());

            // iterate the result and check they are as expected
            for (int i = 0; i < expectedResults.length; i++) {
                assertEquals(expectedResults[i], results.get(i));
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class InvalidStringRangeTest {
        @Parameterized.Parameter(value = 0)
        public String start;

        @Parameterized.Parameter(value = 1)
        public String end;

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"'abc1'", "'abc23'"}, // start and end string length is different
                    {"a11", "'a22'"},      // start and end string does not share the same prefix except last character
                    {"''", "'1'"},         // start and end string length is different
                    {"'1'", "''"},         // start and end string length is different
            });
        }

        @Test(expected = IllegalArgumentException.class)
        public void shouldNotParse() {
            parseGenericLiteralRange(String.format("%s..%s", start, end));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidStringLiteralTest {
        @Parameterized.Parameter(value = 0)
        public String script;

        @Parameterized.Parameter(value = 1)
        public String expected;

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"'a'", "a"},
                    {"'A1'", "A1"},
                    {"'1'", "1"},
                    {"''", "Empty"},
                    {"'10_000'", "10_000"},
                    {"\"a\"", "a"},
                    {"\"A1\"", "A1"},
                    {"\"1\"", "1"},
                    {"\"\"", "Empty"},
                    {"\"10_000\"", "10_000"},
                    // escaped characters according to http://groovy-lang.org/syntax.html#_escaping_special_characters
                    // {} are there just for readability
                    {"'{\\t} {\\b} {\\n} {\\r} {\\f} {\\'} {\\\"} {\\\\}'", "{\t} {\b} {\n} {\r} {\f} {'} {\"} {\\}"},
                    {"\"{\\t} {\\b} {\\n} {\\r} {\\f} {\\'} {\\\"} {\\\\}\"", "{\t} {\b} {\n} {\r} {\f} {'} {\"} {\\}"},
                    // unicode escape
                    {"'\\u2300'", "\u2300"},
                    {"'abc\\u2300def'", "abc\u2300def"},
                    {"'\u2300'", "\u2300"},
                    {"'abc\u2300def'", "abc\u2300def"},
            });
        }

        @Test
        public void shouldParse() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(script));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.StringLiteralContext ctx = parser.stringLiteral();
            if (expected.equals("Empty")) {
                // handle special case for Empty string
                assertEquals("", GenericLiteralVisitor.getInstance().visitStringLiteral(ctx));
            } else {
                assertEquals(expected, GenericLiteralVisitor.getInstance().visitStringLiteral(ctx));
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class AllPrintableAsciiCharactersTest {
        @Parameterized.Parameter(value = 0)
        public String quoteCharacter;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"\""},
                    {"'"},
            });
        }

        @Test
        public void shouldParse() {
            final StringBuilder inputBuilder = new StringBuilder();
            final StringBuilder expectedOutputBuilder = new StringBuilder();

            // build a string which contains all the ASCII printable characters
            // ASCII printable character start from 32 to 126
            for (Character c = 32; c < 127; c++) {
                if ((quoteCharacter.equals(String.valueOf(c))) || (c == '\\')) {
                    // escape this character in the input
                    inputBuilder.append("\\");
                }
                inputBuilder.append(c);
                expectedOutputBuilder.append(c);
            }
            final String inputChars = inputBuilder.toString();
            final String expectedOutputChars = expectedOutputBuilder.toString();

            final String stringLiteral = quoteCharacter + inputChars + quoteCharacter;
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(stringLiteral));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.StringLiteralContext ctx = parser.stringLiteral();
            assertEquals(expectedOutputChars, GenericLiteralVisitor.getInstance().visitStringLiteral(ctx));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidIntegerLiteralTest {
        @Parameterized.Parameter(value = 0)
        public String script;

        @Parameterized.Parameter(value = 1)
        public String expected;

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    // decimal format
                    {"1", "1"},
                    {"-11", "-11"},
                    {"0", "0"},
                    {"1L", "1L"},
                    {"-1l", "-1l"},
                    {"1_2_3", "123"},
                    {"-1_2_3L", "-123L"},
                    {"9223372036854775807", "9223372036854775807"},
                    {"-9223372036854775808", "-9223372036854775808"},
                    {"9223372036854775807L", "9223372036854775807"},
                    {"-9223372036854775808l", "-9223372036854775808"},
                    // hex format
                    {"0xA", "10"},
                    {"-0xA", "-10"},
                    {"0xaL", "10l"},
                    {"-0xal", "-10l"},
                    {"-0xA_0L", "-160l"},
                    {"0x10", "16"},
                    {"-0x10", "-16"},
                    {"0x10", "16"},
                    {"-0x10l", "-16l"},
                    {"-0x1_0L", "-16l"},
                    // oct format
                    {"01", "1"},
                    {"-01", "-1"},
                    {"01L", "1l"},
                    {"-01l", "-1l"},
                    {"010", "8"},
                    {"-010", "-8"},
                    {"010L", "8l"},
                    {"-010l", "-8l"},
                    {"-01_0L", "-8l"},
            });
        }

        @Test
        public void shouldParse() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(script));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.IntegerLiteralContext ctx = parser.integerLiteral();

            final Object actualValue = GenericLiteralVisitor.getInstance().visitIntegerLiteral(ctx);

            // verify suffix L/l
            if (expected.toUpperCase().charAt(expected.length() - 1) == 'L') {
                assertEquals(Long.valueOf(expected.substring(0, expected.length() - 1)), actualValue);
                return;
            }

            // based on value range verify the value is parsed in correct type
            try {
                assertEquals(Integer.valueOf(expected), actualValue);
            } catch (NumberFormatException ignoredException) {
                assertEquals(Long.valueOf(expected), actualValue);
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidBigIntegerLiteralTest {
        @Parameterized.Parameter(value = 0)
        public String script;

        @Parameterized.Parameter(value = 1)
        public String expected;

        @Parameterized.Parameter(value = 2)
        public int radix;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"+0xfffffffffffffffffffffffffffffffffffffffffffffff", "fffffffffffffffffffffffffffffffffffffffffffffff", 16},
                    {"0xfffffffffffffffffffffffffffffffffffffffffffffff", "fffffffffffffffffffffffffffffffffffffffffffffff", 16},
                    {"-0xfffffffffffffffffffffffffffffffffffffffffffffff", "-fffffffffffffffffffffffffffffffffffffffffffffff", 16},
                    {"+9999999999999999999999999999999999999999999999999", "9999999999999999999999999999999999999999999999999", 10},
                    {"9999999999999999999999999999999999999999999999999", "9999999999999999999999999999999999999999999999999", 10},
                    {"-9999999999999999999999999999999999999999999999999", "-9999999999999999999999999999999999999999999999999", 10},
                    {"+0777777777777777777777777777777777777777777777777", "0777777777777777777777777777777777777777777777777", 8},
                    {"0777777777777777777777777777777777777777777777777", "0777777777777777777777777777777777777777777777777", 8},
                    {"-0777777777777777777777777777777777777777777777777", "-0777777777777777777777777777777777777777777777777", 8}
            });
        }

        @Test
        public void shouldParse() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(script));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.IntegerLiteralContext ctx = parser.integerLiteral();

            assertEquals(new BigInteger(expected, radix), GenericLiteralVisitor.getInstance().visitIntegerLiteral(ctx));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidFloatLiteralTest {
        @Parameterized.Parameter(value = 0)
        public String script;

        @Parameterized.Parameter(value = 1)
        public String expected;

        @Parameterized.Parameter(value = 2)
        public String type;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"1.1", "1.1", "java.math.BigDecimal"},
                    {"-0.1", "-0.1", "java.math.BigDecimal"},
                    {"1.0E+12", "1.0E12", "java.math.BigDecimal"},
                    {"-0.1E-12", "-0.1E-12", "java.math.BigDecimal"},
                    {"1E12", "1E12", "java.math.BigDecimal"},
                    // float
                    {"1.1f", "1.1", "java.lang.Float"},
                    {"-0.1F", "-0.1", "java.lang.Float"},
                    {"1.0E+12f", "1.0E12", "java.lang.Float"},
                    {"-0.1E-12F", "-0.1E-12", "java.lang.Float"},
                    {"1E12f", "1E12", "java.lang.Float"},
                    {"1F", "1", "java.lang.Float"},

                    // double
                    {"1.1d", "1.1", "java.lang.Double"},
                    {"-0.1D", "-0.1", "java.lang.Double"},
                    {"1.0E+12d", "1.0E12", "java.lang.Double"},
                    {"-0.1E-12D", "-0.1E-12", "java.lang.Double"},
                    {"1E12d", "1E12", "java.lang.Double"},
                    {"1D", "1", "java.lang.Double"}
            });
        }

        @Test
        public void shouldParse() throws Exception {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(script));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.FloatLiteralContext ctx = parser.floatLiteral();

            final Class<?> clazz = Class.forName(type);
            final Constructor<?> ctor = clazz.getConstructor(String.class);
            final Object expectedValue = ctor.newInstance(expected);

            assertEquals(expectedValue, GenericLiteralVisitor.getInstance().visitFloatLiteral(ctx));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidBooleanLiteralTest {
        @Parameterized.Parameter(value = 0)
        public String script;

        @Parameterized.Parameter(value = 1)
        public boolean expected;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"true", true},
                    {"false", false}
            });
        }

        @Test
        public void shouldParse() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(script));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.BooleanLiteralContext ctx = parser.booleanLiteral();

            assertEquals(expected, GenericLiteralVisitor.getInstance().visitBooleanLiteral(ctx));
        }
    }

    @RunWith(Parameterized.class)
    public static class ValidMapLiteralTest {
        @Parameterized.Parameter(value = 0)
        public String script;

        @Parameterized.Parameter(value = 1)
        public int expectedNumKeysInMap;

        @Parameterized.Parameters()
        public static Iterable<Object[]> generateTestParameters() {
            return Arrays.asList(new Object[][]{
                    {"[\"name\":\"simba\"]", 1},
                    {"[:]", 0},
                    {"[\"name\":\"simba\",\"age\":32]", 2},
                    {"[\"name\":\"simba\",\"age\":[2,3]]", 2}
            });
        }

        @Test
        public void shouldParse() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString(script));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.GenericLiteralContext ctx = parser.genericLiteral();
            final Object genericLiteral = GenericLiteralVisitor.getInstance().visitGenericLiteral(ctx);

            // verify type is Map
            assertThat(genericLiteral, instanceOf(Map.class));

            // verify total number of elements
            final Map<Object, Object> genericLiterals = (Map<Object, Object>) genericLiteral;
            assertEquals(expectedNumKeysInMap, genericLiterals.size());
        }
    }

    public static class ValidListLiteralTest {

        @Test
        public void testGenericLiteralCollection() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString("['world', 165, [12L, 0xA, 14.5, \"hello\"]]"));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.GenericLiteralContext ctx = parser.genericLiteral();
            final Object genericLiteral = GenericLiteralVisitor.getInstance().visitGenericLiteral(ctx);

            // verify type is Object[]
            assertThat(genericLiteral, instanceOf(List.class));

            // verify total number of elements
            List<Object> genericLiterals = (List<Object>) genericLiteral;
            assertEquals(genericLiterals.size(), 3);

            // verify first element
            assertEquals("world", genericLiterals.get(0));

            // verify second element
            assertEquals(165, genericLiterals.get(1));

            // verify 3rd element
            assertThat(genericLiterals.get(2), instanceOf(List.class));

            // verify total number of elements
            genericLiterals = (List<Object>) genericLiterals.get(2);
            assertEquals(genericLiterals.size(), 4);

            // verify first element
            assertEquals(12L, genericLiterals.get(0));

            // verify second element
            assertEquals(10, genericLiterals.get(1));

            // verify 3rd element
            assertEquals(new BigDecimal(14.5), genericLiterals.get(2));

            // verify 4th element
            assertEquals("hello", genericLiterals.get(3));
        }

        /**
         * Generic literal collection test
         */
        @Test
        public void testEmptyGenericLiteralCollection() {
            final GremlinLexer lexer = new GremlinLexer(CharStreams.fromString("[]"));
            final GremlinParser parser = new GremlinParser(new CommonTokenStream(lexer));
            final GremlinParser.GenericLiteralContext ctx = parser.genericLiteral();
            final Object genericLiteral = GenericLiteralVisitor.getInstance().visitGenericLiteral(ctx);

            // verify type is Object[]
            assertThat(genericLiteral, instanceOf(List.class));

            // verify total number of elements
            final List<Object> genericLiterals = (List<Object>) genericLiteral;
            Assert.assertTrue(genericLiterals.isEmpty());
        }
    }
}
