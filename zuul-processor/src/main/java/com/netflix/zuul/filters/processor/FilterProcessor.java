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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.Splitter;
import com.netflix.zuul.Filter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

    private final Map<String, Set<Element>> packageToElements = new TreeMap<>(String::compareTo);

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(Filter.class);
        Elements elementUtils = processingEnv.getElementUtils();
        for (Element el : annotated) {
            if (el.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
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

    @VisibleForTesting
    static void writeFile(Writer writer, String packageName, String className, Collection<? extends Element> elements)
            throws IOException {
        writer.write("package " + packageName + ";\n");
        writer.write("\n");
        writer.write("@javax.annotation.Generated(\"by: \\\"" + FilterProcessor.class.getName() + "\\\"\")\n");
        writer.write("public final class " + className + " {\n");
        writer.write("\n");
        writer.write("    private " + className + "() {}\n");
        writer.write("\n");
        writer.write("    public static java.util.List<? extends java.lang.Class<\n");
        writer.write("            ? extends com.netflix.zuul.filters.ZuulFilter<?, ?>>> getFilters() {\n");
        writer.write("        return FILTERS;\n");
        writer.write("    }\n");
        writer.write("\n");
        writer.write("    private static final java.util.List<? extends java.lang.Class<\n");
        writer.write("            ? extends com.netflix.zuul.filters.ZuulFilter<?, ?>>> FILTERS =\n");
        writer.write("        java.util.Collections.unmodifiableList(java.util.Arrays.asList(\n");
        int i = 0;
        for (Element element : elements) {
            writer.write("                " + element + ".class");
            if (++i < elements.size()) {
                writer.write(',');
            }
            writer.write('\n');
        }
        writer.write("    ));\n");
        writer.write("}\n");
    }

    private static void writeFiles(Filer filer, Map<String, Set<Element>> packageToElements) throws Exception {
        for (Entry<String, Set<Element>> entry : packageToElements.entrySet()) {
            String pkg = entry.getKey();
            List<Element> elements = new ArrayList<>(entry.getValue());
            String className = deriveGeneratedClassName(pkg);
            JavaFileObject source = filer.createSourceFile(pkg + "." +  className, elements.toArray(new Element[0]));
            try (Writer writer = source.openWriter()) {
                writeFile(writer, pkg, className, elements);
            }
        }
    }

    @VisibleForTesting
    static String deriveGeneratedClassName(String packageName) {
        Objects.requireNonNull(packageName, "packageName");
        List<String> parts = Splitter.on('.').splitToList(packageName);
        Converter<String, String> converter = CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL);
        String baseName = "";
        switch (parts.size()) {
            default:
                // fallthrough
            case 2:
                baseName += converter.convert(parts.get(parts.size() - 2));
                // fallthrough
            case 1:
                baseName += converter.convert(parts.get(parts.size() - 1));
                // fallthrough
            case 0:
                baseName += "Filters";
        }
        return baseName;
    }
}
