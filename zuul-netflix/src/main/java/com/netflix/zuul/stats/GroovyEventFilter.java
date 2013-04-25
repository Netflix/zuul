/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.stats;


import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer.ExpressionChecker;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer.StatementChecker;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Helper methods used to compile and run Groovy event filters.
 *
 * @author mhawthorne
 */
public class GroovyEventFilter {

    private static final String NULL = ":null";


    private static final Logger LOG = LoggerFactory.getLogger(GroovyEventFilter.class);


    private static final CompilerConfiguration COMPILER_CONFIG = new CompilerConfiguration() {{
        final SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setClosuresAllowed(false);
        secure.setMethodDefinitionAllowed(false);

        final ExpressionChecker expressionChecker = new ExpressionChecker() {
            public boolean isAuthorized(Expression e) {
                // limiting the types of expressions that can be used in event filters
                boolean authorized = (e instanceof ConstantExpression ||
                    e instanceof VariableExpression ||
                    e instanceof BinaryExpression ||
                    e instanceof BooleanExpression ||
                    e instanceof PropertyExpression);

                // prevents access to java.lang.System
                // this is very crude but good enough for now
                final String text = e.getText();
                if (text.contains("System.")) {
                    authorized = false;
                }

                return authorized;
            }
        };

        final StatementChecker statementChecker = new StatementChecker() {
            public boolean isAuthorized(Statement statement) {
                return true;
            }
        };

        secure.addExpressionCheckers(expressionChecker);
        secure.addStatementCheckers(statementChecker);

        addCompilationCustomizers(secure);
    }};

    /**
     * Static Groovy class loader.  I may switch to this model to (theoretically) conserve PermGen space.
     */
    private static final GroovyClassLoader GROOVY_CLASS_LOADER = newGroovyClassLoader();

    /**
     * Dictates whether we use a static GroovyClassLoader or create a new one for each new client.
     */
    private static final DynamicBooleanProperty USE_STATIC_GROOVY_CLASS_LOADER =
        DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.groovy.use-static-classloader", false);

    private static final GroovyClassLoader newGroovyClassLoader() {
        return new GroovyClassLoader(GroovyEventFilter.class.getClassLoader(), COMPILER_CONFIG);
    }

    /**
     * Either fetches static GroovyClassLoader or creates a new one, depending on configuration.
     */
    private static final GroovyClassLoader getGroovyClassLoader() {
        return USE_STATIC_GROOVY_CLASS_LOADER.get() ? GROOVY_CLASS_LOADER : newGroovyClassLoader();
    }

    public static final Class compileFilter(String code) {
        return getGroovyClassLoader().parseClass(code);
    }

    public static final Boolean runBooleanFilter(String rawFilter, Class compiledFilter, final ZuulEvent event) {
        final Binding b = new Binding() {
            @Override
            public Object getVariable(String name) {
                Object v = event.get(name);
                return (v != null) ? v : getVariableSafely(name);
            }

            private Object getVariableSafely(String name) {
                try {
                    return super.getVariable(name);
                } catch (Exception e) {
                    // this will execute for referenced variables that do not exist.
                    // I return the magical NULL string to allow for more user-friendly queries
                    return null;
                }
            }

        };
        b.setVariable("e", event);

        // sets all event attributes as top level variables in binding
        for(final Iterator<String> i = event.keys(); i.hasNext();) {
            String key = i.next();

            Object val = event.get(key);

            // replaces dash with underscore since dash can't be used in groovy vars
            final String normalizedKey = key.replaceAll("[\\.-]", "_");

            event.set(key, val);
            b.setVariable(normalizedKey, val);
        }

        Object result;

        try {
            result = InvokerHelper.createScript(compiledFilter, b).run();
        } catch (RuntimeException e) {
            LOG.error(String.format("error applying query '%s' to event: %s", rawFilter, event.toStringMap()), e);
            throw e;
        }

        if(!(result instanceof Boolean)) {
            throw new IllegalArgumentException(String.format("Groovy event filter \"%s\" does not return a boolean", rawFilter));
        }

        return (Boolean) result;
    }

    public static final class UnitTest {

        private static final ZuulEvent newEvent() {
            return new ZuulEvent();
        }

        @Test
        public void compileFilterForInvalidGroovy() {
            final Class c = GroovyEventFilter.compileFilter("hi");
            assertNotNull(c);
        }

        @Test
        public void compileFilterForValidGroovy() {
            final Class c = GroovyEventFilter.compileFilter("true");
            assertNotNull(c);
        }

        @Test
        public void compileFilterDisallowsRefsToSystemExit() {
            try {
                final Class compiled = GroovyEventFilter.compileFilter("System.exit(5)");
                fail("exception expected");
            } catch (CompilationFailedException e) {}
        }

        @Test
        public void compileFilterDisallowsRefsToSystemErr() {
            try {
                final Class compiled = GroovyEventFilter.compileFilter("System.err.println(\"hi\")");
                fail("exception expected");
            } catch (CompilationFailedException e) {}
        }

        @Test
        public void runsConstantTrueBooleanFilter() {
            final String raw = "true";
            final Class compiled = GroovyEventFilter.compileFilter(raw);
            final ZuulEvent e = newEvent();
            assertTrue(GroovyEventFilter.runBooleanFilter(raw, compiled, e));
        }

        @Test
        public void runsConstantFalseBooleanFilter() {
            final String raw = "false";
            final Class compiled = GroovyEventFilter.compileFilter(raw);
            final ZuulEvent e = newEvent();
            assertFalse(GroovyEventFilter.runBooleanFilter(raw, compiled, e));
        }

//        @Test
        public void failsForFilterWithInvalidSyntax() {
            final String raw = "hi";
            final Class compiled = GroovyEventFilter.compileFilter(raw);
            final ZuulEvent e = newEvent();
            try {
                GroovyEventFilter.runBooleanFilter(raw, compiled, e);
                fail("exception expected");
            } catch (MissingPropertyException ex) {}
        }

        @Test
        public void failsForFilterThatReturnsNonBoolean() {
            final String raw = "\"hi\"";
            final Class compiled = GroovyEventFilter.compileFilter(raw);
            final ZuulEvent e = newEvent();
            try {
                GroovyEventFilter.runBooleanFilter(raw, compiled, e);
                fail("exception expected");
            } catch (IllegalArgumentException ex) {}
        }

        @Test
        public void runsValidTrueBooleanFilter() {
            final String raw = "esn==\"hi\"";
            final Class compiled = GroovyEventFilter.compileFilter(raw);
            final ZuulEvent e = newEvent();
            e.set("esn", "hi");
            assertTrue(GroovyEventFilter.runBooleanFilter(raw, compiled, e));
        }

    }

}
