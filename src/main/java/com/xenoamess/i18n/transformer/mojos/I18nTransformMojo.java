/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.xenoamess.i18n.transformer.mojos;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.xenoamess.i18n.transformer.contexts.I18nTransformerContext;
import com.xenoamess.i18n.transformer.entities.PropertiesEntity;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.xenoamess.i18n.transformer.utils.I18nTransformerUtil.dfs;

@Mojo(name = "transform", requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = false)
@Execute(phase = LifecyclePhase.COMPILE)
public class I18nTransformMojo extends AbstractMojo {

    // ----------------------------------------------------------------------
    // Mojo parameters
    // ----------------------------------------------------------------------

    @Parameter(property = "javaVersion", defaultValue = "JAVA_11")
    private String javaVersion;

    @Parameter(property = "propertyBundleName", defaultValue = "${project.name}_xi18nt")
    private String propertyBundleName;

    @Parameter(property = "i18nTemplate", defaultValue = "java.util.ResourceBundle.getBundle(\"$${propertyBundleName}\", java.util.Locale.CHINA).getString(\"$${value}\")")
    private String i18nTemplate;

    @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /**
     * Java Files Pattern.
     */
    public static final String JAVA_FILES = "**\\/*.java";

    /**
     * Comma separated includes Java files, i.e. <code>&#42;&#42;/&#42;Test.java</code>.
     * <p/>
     * <strong>Note:</strong> default value is {@code **\/*.java}.
     */
    @Parameter(property = "includes", defaultValue = JAVA_FILES)
    private String includes;

    /**
     * Comma separated excludes Java files, i.e. <code>&#42;&#42;/&#42;Test.java</code>.
     */
    @Parameter(property = "excludes")
    private String excludes;

    /**
     * The Maven Project Object.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * The current user system settings for use in Maven.
     */
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    private Settings settings;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // run qdox and process
        try {
            if ("pom".equalsIgnoreCase(project.getPackaging())) {
                getLog().warn("This project has 'pom' packaging, no Java sources is available.");
                return;
            }

            List<File> javaFiles = new LinkedList<>();
            for (String sourceRoot : getProjectSourceRoots(project)) {
                File f = new File(sourceRoot);
                if (f.isDirectory()) {
                    javaFiles.addAll(FileUtils.getFiles(f, includes, excludes, true));
                } else {
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(f + " doesn't exist. Ignored it.");
                    }
                }
            }

            ParserConfiguration parserConfiguration = new ParserConfiguration();
            parserConfiguration.setCharacterEncoding(Charset.forName(encoding));
            parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.valueOf(javaVersion));


            List<PropertiesEntity> propertiesEntityList = new ArrayList<>();
            for (File f : javaFiles) {
                if (!f.getAbsolutePath().toLowerCase(Locale.ENGLISH).endsWith(".java") && getLog().isWarnEnabled()) {
                    continue;
                }
                String handledFileContent = null;
                I18nTransformerContext i18nTransformerContext = new I18nTransformerContext(
                        i18nTemplate,
                        propertyBundleName,
                        f.getPath(),
                        f.getAbsolutePath(),
                        0,
                        new ArrayList<>()
                );
                try (
                        InputStream inputStream = new FileInputStream(f);
                        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)
                ) {
                    CompilationUnit compilationUnit = StaticJavaParser.parse(bufferedInputStream, Charset.forName(encoding));
                    dfs(
                            compilationUnit,
                            i18nTransformerContext
                    );
                    propertiesEntityList.addAll(i18nTransformerContext.getChinesePropertiesEntities());
                    handledFileContent = compilationUnit.toString();
                }
                if (handledFileContent != null && !i18nTransformerContext.getChinesePropertiesEntities().isEmpty()) {
                    try (
                            OutputStream outputStream = new FileOutputStream(f);
                            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)
                    ) {
                        bufferedOutputStream.write(
                                handledFileContent.getBytes(Charset.forName(encoding))
                        );
                    }
                }
            }
            if (!propertiesEntityList.isEmpty()) {
                StringBuilder stringBuilder = new StringBuilder();
                for (PropertiesEntity propertiesEntity : propertiesEntityList) {
                    stringBuilder.append(propertiesEntity.getPropertyName());
                    stringBuilder.append("=");
                    stringBuilder.append(propertiesEntity.getChineseValue());
                    stringBuilder.append('\n');
                }
                org.apache.commons.io.FileUtils.write(
                        new File(
                                project.getBasedir(),
                                "src/main/resources/" + propertyBundleName + ".properties"
                        ),
                        stringBuilder.toString(),
                        StandardCharsets.UTF_8
                );
                org.apache.commons.io.FileUtils.write(
                        new File(
                                project.getBasedir(),
                                "src/main/resources/" + propertyBundleName + "_zh_CN.properties"
                        ),
                        stringBuilder.toString(),
                        StandardCharsets.UTF_8
                );
            }
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------------
    // protected methods
    // ----------------------------------------------------------------------

    protected final MavenProject getProject() {
        return project;
    }

    /**
     * @param p not null maven project.
     * @return the artifact type.
     */
    protected String getArtifactType(MavenProject p) {
        return p.getArtifact().getType();
    }

    /**
     * @param p not null maven project.
     * @return the list of source paths for the given project.
     */
    protected List<String> getProjectSourceRoots(MavenProject p) {
        return (p.getCompileSourceRoots() == null
                ? Collections.<String>emptyList()
                : new LinkedList<>(p.getCompileSourceRoots()));
    }

    /**
     * @param p not null
     * @return the compile classpath elements
     * @throws DependencyResolutionRequiredException if any
     */
    protected List<String> getCompileClasspathElements(MavenProject p) throws DependencyResolutionRequiredException {
        return (p.getCompileClasspathElements() == null
                ? Collections.<String>emptyList()
                : new LinkedList<>(p.getCompileClasspathElements()));
    }

    // ----------------------------------------------------------------------
    // private methods
    // ----------------------------------------------------------------------


    /**
     * @return the source dir as File for the given project
     */
    private File getProjectSourceDirectory() {
        return new File(project.getBuild().getSourceDirectory());
    }


    /**
     * Autodetect the indentation of a given line:
     * <pre>
     * autodetectIndentation( null ) = "";
     * autodetectIndentation( "a" ) = "";
     * autodetectIndentation( "    a" ) = "    ";
     * autodetectIndentation( "\ta" ) = "\t";
     * </pre>
     *
     * @param line not null
     * @return the indentation for the given line.
     */
    private static String autodetectIndentation(final String line) {
        if (StringUtils.isEmpty(line)) {
            return "";
        }

        return line.substring(0, line.indexOf(trimLeft(line)));
    }

    /**
     * @param content not null
     * @return an array of all content lines
     * @throws IOException if any
     */
    private static String[] getLines(final String content) throws IOException {
        List<String> lines = new LinkedList<>();

        BufferedReader reader = new BufferedReader(new StringReader(content));
        String line = reader.readLine();
        while (line != null) {
            lines.add(line);
            line = reader.readLine();
        }

        return lines.toArray(new String[lines.size()]);
    }

    /**
     * Trim a given line on the left:
     * <pre>
     * trimLeft( null ) = "";
     * trimLeft( "  " ) = "";
     * trimLeft( "a" ) = "a";
     * trimLeft( "    a" ) = "a";
     * trimLeft( "\ta" ) = "a";
     * trimLeft( "    a    " ) = "a    ";
     * </pre>
     *
     * @param text
     * @return the text trimmed on left side or empty if text is null.
     */
    private static String trimLeft(final String text) {
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(text.trim())) {
            return "";
        }

        String textTrimmed = text.trim();
        return text.substring(text.indexOf(textTrimmed));
    }

    /**
     * Trim a given line on the right:
     * <pre>
     * trimRight( null ) = "";
     * trimRight( "  " ) = "";
     * trimRight( "a" ) = "a";
     * trimRight( "a\t" ) = "a";
     * trimRight( "    a    " ) = "    a";
     * </pre>
     *
     * @param text
     * @return the text trimmed on tight side or empty if text is null.
     */
    private static String trimRight(final String text) {
        if (StringUtils.isEmpty(text) || StringUtils.isEmpty(text.trim())) {
            return "";
        }

        String textTrimmed = text.trim();
        return text.substring(0, text.indexOf(textTrimmed) + textTrimmed.length());
    }

}
