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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;


@SupportedAnnotationTypes(FilterProcessor.FILTER_TYPE)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public final class FilterProcessor extends AbstractProcessor {

    static final String FILTER_TYPE = "com.netflix.zuul.Filter";

    private final Set<String> annotatedElements = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotated =
                roundEnv.getElementsAnnotatedWith(processingEnv.getElementUtils().getTypeElement(FILTER_TYPE));
        for (Element el : annotated) {
            if (el.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            annotatedElements.add(processingEnv.getElementUtils().getBinaryName((TypeElement) el).toString());
        }

        if (roundEnv.processingOver()) {
            try {
                addNewClasses(processingEnv.getFiler(), annotatedElements);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                annotatedElements.clear();
            }
        }
        return true;
    }

    static void addNewClasses(Filer filer, Collection<String> elements) throws IOException {
        String resourceName = "META-INF/zuul/allfilters";
        List<String> existing = Collections.emptyList();
        try {
            FileObject existingFilters = filer.getResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
            try (InputStream is = existingFilters.openInputStream();
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                existing = readResourceFile(reader);
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            // Perhaps log this.
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int sizeBefore = existing.size();
        Set<String> existingSet = new LinkedHashSet<>(existing);
        List<String> newElements = new ArrayList<>(existingSet);
        for (String element : elements) {
            if (existingSet.add(element)) {
                newElements.add(element);
            }
        }
        if (newElements.size() == sizeBefore) {
            // nothing to do.
            return;
        }
        newElements.sort(String::compareTo);

        FileObject dest = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resourceName);
        try (OutputStream os = dest.openOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            writeResourceFile(osw, newElements);
        }
    }

    @VisibleForTesting
    static List<String> readResourceFile(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String line;
        List<String> lines = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            lines.add(line);
        }
        return Collections.unmodifiableList(lines);
    }

    @VisibleForTesting
    static void writeResourceFile(Writer writer, Collection<?> elements) throws IOException {
        BufferedWriter bw = new BufferedWriter(writer);
        for (Object element : elements) {
            bw.write(String.valueOf(element));
            bw.newLine();
        }
        bw.flush();
    }
}
