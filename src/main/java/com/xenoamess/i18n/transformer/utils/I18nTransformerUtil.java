package com.xenoamess.i18n.transformer.utils;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.xenoamess.i18n.transformer.contexts.I18nTransformerContext;
import com.xenoamess.i18n.transformer.entities.PropertiesEntity;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class I18nTransformerUtil {

    public static void dfs(
            @Nullable Node node,
            @NotNull I18nTransformerContext i18nTransformerContext
    ) {
        if (node == null) {
            return;
        }
        if (node instanceof StringLiteralExpr) {
            handleStringLiteralExpr((StringLiteralExpr) node, i18nTransformerContext);
            return;
        }
        if (node instanceof ClassOrInterfaceDeclaration) {
            Optional<String> classFullNameOptional = ((ClassOrInterfaceDeclaration) node).getFullyQualifiedName();
            String prefixKey = classFullNameOptional.map(
                    classFullName -> i18nTransformerContext.getPropertyBundleName() + "." + classFullName
            ).orElseGet(
                    () -> i18nTransformerContext.getPropertyBundleName() + "." + "default"
            );
            i18nTransformerContext.setPrefixKey(prefixKey);
            i18nTransformerContext.setCurrentIndex(0);
        }
        List<Node> childrenMirror = new ArrayList<>(node.getChildNodes());
        for (Node child : childrenMirror) {
            if (child.getParentNode().isPresent()) {
                dfs(child, i18nTransformerContext);
            }
        }
    }

    private static final Pattern PATTERN_CHINESE = Pattern.compile(".*[\\u4E00-\\u9FA5]+.*");

    @NotNull
    private static String handleString(
            @NotNull String originalString,
            @NotNull I18nTransformerContext i18nTransformerContext
    ) {
        String prefixKey = i18nTransformerContext.getPrefixKey();

        int currentIndex = i18nTransformerContext.getCurrentIndex();
        ++currentIndex;
        i18nTransformerContext.setCurrentIndex(currentIndex);

        String chineseValue = originalString;

        String propertyName = prefixKey + "." + currentIndex;

        i18nTransformerContext.getChinesePropertiesEntities().add(
                new PropertiesEntity(
                        propertyName,
                        chineseValue
                )
        );

        String result = i18nTransformerContext.getI18nTemplate();
        result = StringUtils.replace(
                result,
                "${value}",
                propertyName
        );
        result = StringUtils.replace(
                result,
                "${propertyBundleName}",
                i18nTransformerContext.getPropertyBundleName()
        );
        return result;
    }

    private static void handleStringLiteralExpr(
            @NotNull StringLiteralExpr node,
            @NotNull I18nTransformerContext i18nTransformerContext
    ) {
        if (!PATTERN_CHINESE.matcher(node.getValue()).matches()) {
            // not Chinese
            return;
        }
        Optional<Node> parentOptional = node.getParentNode();
        if (parentOptional.isEmpty()) {
            System.err.println("warn: StringLiteralExpr have no parent : " + node + " at file : " + i18nTransformerContext.getIdentifier());
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
                            System.err.println("warn: cannot change static field: " + parentParentNode + " at file : " + i18nTransformerContext.getIdentifier());
                        } else {
                            Optional<Expression> initializer = variableDeclarator.getInitializer();
                            variableDeclarator.setInitializer(
                                    handleString(
                                            node.getValue(),
                                            i18nTransformerContext
                                    )
                            );
                        }
                    } else if (parentParentNode instanceof VariableDeclarationExpr) {
                        Optional<Expression> initializer = variableDeclarator.getInitializer();
                        variableDeclarator.setInitializer(
                                handleString(
                                        node.getValue(),
                                        i18nTransformerContext
                                )
                        );
                    }
                }
            }
            return;
        } else if (parentNode instanceof EnumConstantDeclaration) {
            System.err.println("warn: EnumConstantDeclaration need manual change : " + node + " at file : " + i18nTransformerContext.getIdentifier());
        } else if (parentNode instanceof NodeWithArguments) {
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
                                handleString(
                                        node.getValue(),
                                        i18nTransformerContext
                                )
                        )
                );
            }

        } else if (parentNode instanceof ArrayInitializerExpr) {
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
                                handleString(
                                        node.getValue(),
                                        i18nTransformerContext
                                )
                        )
                );
            }
            ((ArrayInitializerExpr) parentNode).setValues(
                    arguments
            );
        } else {
            System.err.println("unhandled class : " + parentNode.getClass().getName() + " at file : " + i18nTransformerContext.getIdentifier());
        }

    }

}
