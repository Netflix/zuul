/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.zuul;

import com.netflix.config.Property;
import com.netflix.zuul.filters.ZuulFilter;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethod;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import static org.junit.Assert.assertNotNull;

/**

 This test uses the ArchUnit library to enforce coding conventions within the Zuul project.

 ArchUnit @AnalyzeClasses scans the classpath for classes within the com.netflix.zuul
 package hierarchy.

 ArchUnit rules enforce consistency within the project.

 "Stopping entropy with ArchUnit"
  https://www.youtube.com/watch?v=AOKqpnCDtWU

 */
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "com.netflix.zuul", importOptions = { DoNotIncludeTests.class })
public class ArchUnitTest {

    @ArchTest
    public static ArchRule NO_JAVA_UTIL_LOGGING = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    public static ArchRule LOGGER_FIELDS_ARE_FINAL = fields()
                                    .that()
                                    .haveRawType(org.slf4j.Logger.class)
                                    .should()
                                    .beFinal();

    @ArchTest
    public static ArchRule LOGGER_FIELDS_ARE_NOT_PUBLIC = fields()
                                    .that()
                                    .haveRawType(org.slf4j.Logger.class)
                                    .should()
                                    .notBePublic();

    @ArchTest
    public static ArchRule FILTER_CLASS_HAS_FILTER_ANNOTATION = classes()
                                    .that()
                                    .implement(ZuulFilter.class)
                                    .and()
                                    .doNotHaveModifier(JavaModifier.ABSTRACT)
                                    .and()
                                    .areNotAnonymousClasses()
                                    .should()
                                    .beAnnotatedWith(Filter.class);

    @ArchTest
    public static ArchRule STATIC_PROPERTY_FIELDS_SHOULD_BE_FINAL = fields()
                                    .that()
                                    .haveRawType(assignableTo(Property.class))
                                    .and()
                                    .areStatic()
                                    .should()
                                    .beFinal();

    // disallow String.replaceAll because this method uses Pattern.compile under the hood
    // https://twitter.com/gunnarmorling/status/1333363613877342211
    @ArchTest
    public static ArchRule DISALLOW_STRING_REPLACEALL_METHOD = disallowMethod(String.class, "replaceAll", String.class, String.class);

    // disallow String.matches because this method uses Pattern.compile under the hood
    @ArchTest
    public static ArchRule DISALLOW_STRING_MATCHES_METHOD = disallowMethod(String.class, "matches", String.class);

    private static ArchRule disallowMethod(Class<?> clazz, String methodName, Class<?>... methodParameterTypes) {
        final Method method = MethodUtils.getAccessibleMethod(clazz, methodName, methodParameterTypes);
        assertNotNull(method);
        return noClasses().should(callMethod(clazz, methodName, methodParameterTypes));
    }
}
