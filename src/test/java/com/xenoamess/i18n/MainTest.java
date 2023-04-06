package com.xenoamess.i18n;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.quality.NotNull;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class MainTest {

    public static void main(
            String[] args
    ) {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setCharacterEncoding(StandardCharsets.UTF_8);
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        try (InputStream inputStream = MainTest.class.getResourceAsStream("/Main.java")) {
            CompilationUnit compilationUnit = StaticJavaParser.parse(inputStream, StandardCharsets.UTF_8);
            dfs(compilationUnit);
            System.out.println(compilationUnit.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void dfs(Node node) {
        if (node instanceof StringLiteralExpr) {
            handleStringLiteralExpr((StringLiteralExpr) node);
            return;
        }
        List<Node> childrenMirror = new ArrayList<>(node.getChildNodes());
        for (Node child : childrenMirror) {
            if (child.getParentNode().isPresent()) {
                dfs(child);
            }
        }
    }

    private static final Pattern PATTERN_CHINESE = Pattern.compile(".*[\\u4E00-\\u9FA5]+.*");

    private static void handleStringLiteralExpr(@NotNull StringLiteralExpr node) {
        if (!PATTERN_CHINESE.matcher(node.getValue()).matches()) {
            // not Chinese
            return;
        }
        Optional<Node> parentOptional = node.getParentNode();
        if (parentOptional.isEmpty()) {
            System.err.println("warn: StringLiteralExpr have no parent : " + node);
            return;
        }
        Node parentNode = parentOptional.get();
        if (parentNode instanceof VariableDeclarator) {
            VariableDeclarator variableDeclarator = (VariableDeclarator) parentNode;
            Optional<Node> parentParentOptional = variableDeclarator.getParentNode();
            Optional<Expression> expressionOptional = variableDeclarator.getInitializer();
            if (expressionOptional.isPresent()) {
                if (parentParentOptional.isPresent()) {
                    Node parentParentNode = parentParentOptional.get();
                    if (parentParentNode instanceof FieldDeclaration) {
                        if (((FieldDeclaration) parentParentNode).isStatic()) {
                            System.err.println("warn: cannot change static field: " + parentParentNode);
                        } else {
                            Optional<Expression> initializer = variableDeclarator.getInitializer();
                            variableDeclarator.setInitializer(
                                    "I18nUtils.toI18n(\"aaa\")"
                            );
                        }
                    } else if (parentParentNode instanceof VariableDeclarationExpr) {
                        Optional<Expression> initializer = variableDeclarator.getInitializer();
                        variableDeclarator.setInitializer(
                                "I18nUtils.toI18n(\"aaa\")"
                        );
                    }
                }
            }
            return;
        } else if(parentNode instanceof EnumConstantDeclaration) {
            System.err.println("warn: EnumConstantDeclaration need manual change : " + node);
        }  else if (parentNode instanceof NodeWithArguments) {
            NodeList<Expression> arguments = ((NodeWithArguments<?>) parentNode).getArguments();
            List<Pair<Integer, String>> modifyList = new ArrayList<>(arguments.size());
            int i = -1;
            for (Expression expression : arguments) {
                ++i;
                if (expression instanceof StringLiteralExpr) {
                    if (PATTERN_CHINESE.matcher(((StringLiteralExpr) expression).getValue()).matches()) {
                        modifyList.add(
                                Pair.of(
                                        i,
                                        ((StringLiteralExpr) expression).getValue()
                                )

                        );
                    }
                }
            }

            for (Pair<Integer, String> modifySingle : modifyList) {
                ((NodeWithArguments<?>) parentNode).setArgument(
                        modifySingle.getKey(),
                        new NameExpr(
                                "I18nUtils.toI18n(\"aaa\")"
                        )
                );
            }

        } else if(parentNode instanceof ArrayInitializerExpr){
            NodeList<Expression> arguments = ((ArrayInitializerExpr) parentNode).getValues();
            List<Pair<Integer, String>> modifyList = new ArrayList<>(arguments.size());
            int i = -1;
            for (Expression expression : arguments) {
                ++i;
                if (expression instanceof StringLiteralExpr) {
                    if (PATTERN_CHINESE.matcher(((StringLiteralExpr) expression).getValue()).matches()) {
                        modifyList.add(
                                Pair.of(
                                        i,
                                        ((StringLiteralExpr) expression).getValue()
                                )

                        );
                    }
                }
            }

            for (Pair<Integer, String> modifySingle : modifyList) {
                arguments.set(
                        modifySingle.getKey(),
                        new NameExpr(
                                "I18nUtils.toI18n(\"aaa\")"
                        )
                );
            }
            ((ArrayInitializerExpr) parentNode).setValues(
                    arguments
            );
        } else {
            System.err.println("unhandled class : " + parentNode.getClass().getName());
        }

    }

}