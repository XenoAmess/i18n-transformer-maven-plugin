package com.xenoamess.i18n.transformer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.xenoamess.i18n.transformer.contexts.I18nTransformerContext;
import com.xenoamess.i18n.transformer.entities.PropertiesEntity;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

import static com.xenoamess.i18n.transformer.utils.I18nTransformerUtil.dfs;

public class MainTest {

    @Test
    public void test1() {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setCharacterEncoding(StandardCharsets.UTF_8);
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        I18nTransformerContext i18nTransformerContext = new I18nTransformerContext(
                "toI18n(\"${value}\")",
                "x18nt",
                "Main.java",
                null,
                0,
                new ArrayList<>()
        );
        try (InputStream inputStream = MainTest.class.getResourceAsStream("/Main.java")) {
            CompilationUnit compilationUnit = StaticJavaParser.parse(inputStream, StandardCharsets.UTF_8);
            dfs(
                    compilationUnit,
                    i18nTransformerContext
            );
            System.out.println(compilationUnit.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (PropertiesEntity propertiesEntity : i18nTransformerContext.getChinesePropertiesEntities()) {
            stringBuilder.append(propertiesEntity.getPropertyName());
            stringBuilder.append("=");
            stringBuilder.append(propertiesEntity.getChineseValue());
            stringBuilder.append('\n');
        }
        try {
            FileUtils.write(
                    FileUtils.getFile("src/test/resources/Main.properties"),
                    stringBuilder.toString(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test2() {
        ResourceBundle resourceBundle = ResourceBundle.getBundle("Main", Locale.CHINA);
        String s = resourceBundle.getString("x18nt.com.xenoamess.i18n.Main.1");
        System.out.println(s);
    }

}
