/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.zuul.filters.processor;

import com.netflix.zuul.Filter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("com.netflix.zuul.Filter")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class FilterProcessor extends AbstractProcessor {

    private final Map<String, Set<Element>> packageToElements = new TreeMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(Filter.class);
        Elements elementUtils = processingEnv.getElementUtils();
        for (Element el : annotated) {
            el.getModifiers().contains(Modifier.ABSTRACT);
            packageToElements.computeIfAbsent(
                    String.valueOf(elementUtils.getPackageOf(el).getQualifiedName()), k -> new LinkedHashSet<>())
                    .add(el);
        }

        if (!annotated.isEmpty()) {
            try {
                writeFiles(processingEnv.getFiler(), packageToElements);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                packageToElements.clear();
            }
            return true;
        }

        return true;
    }

    private static void writeFiles(Filer filer, Map<String, Set<Element>> packageToElements) throws Exception {
        for (Entry<String, Set<Element>> entry : packageToElements.entrySet()) {
            String pkg = entry.getKey();
            List<Element> elements = new ArrayList<>(entry.getValue());
            JavaFileObject source = filer.createSourceFile(pkg + ".AllFilters", elements.toArray(new Element[0]));
            try (Writer w = source.openWriter()) {
                w.write("package " + pkg + ";\n");
                w.write("\n");
                w.write("@javax.annotation.Generated(\"by: \\\"" + FilterProcessor.class.getName() + "\\\"\")\n");
                w.write("public final class AllFilters {\n");
                w.write("\n");
                w.write("    private AllFilters () {}\n");
                w.write("\n");
                w.write("    public static final java.util.List<? extends java.lang.Class<\n");
                w.write("            ? extends com.netflix.zuul.filters.ZuulFilter<?, ?>>> FILTERS =\n");
                w.write("        java.util.Collections.unmodifiableList(java.util.Arrays.asList(\n");
                for (int i = 0; i < elements.size(); i++) {
                    w.write("                " + elements.get(i) + ".class");
                    if (i != elements.size() - 1) {
                        w.write(',');
                    }
                    w.write('\n');
                }
                w.write("    ));\n");
                w.write("}\n");
            }
        }
    }
}
