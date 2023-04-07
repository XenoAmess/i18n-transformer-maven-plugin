package com.xenoamess.i18n.transformer.utils;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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

        {
            // remove duplication logic
            boolean ifAlreadyIn = false;
            for (PropertiesEntity propertiesEntity : i18nTransformerContext.getChinesePropertiesEntities()) {
                if (StringUtils.equals(propertiesEntity.getChineseValue(), chineseValue)) {
                    propertyName = propertiesEntity.getPropertyName();
                    ifAlreadyIn = true;
                    break;
                }
            }
            if (!ifAlreadyIn) {
                i18nTransformerContext.getChinesePropertiesEntities().add(
                        new PropertiesEntity(
                                propertyName,
                                chineseValue
                        )
                );
            }
        }

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
        String identifier = i18nTransformerContext.getIdentifier();
        String[] identifiers = identifier.split("[\\\\/.]+");
        boolean haveJava = false;
        String className = null;
        for (int i = identifiers.length - 1; i >= 0; i--) {
            if (!haveJava) {
                if ("java".equalsIgnoreCase(identifiers[i])) {
                    haveJava = true;
                }
            } else {
                if (StringUtils.isNotBlank(identifiers[i])) {
                    className = identifiers[i];
                    break;
                }
            }
        }
        if (className == null) {
            className = "Object";
        }
        result = StringUtils.replace(
                result,
                "${classSimpleName}",
                className
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
                        Optional<Expression> initializer = variableDeclarator.getInitializer();
                        String handleResultString = handleString(
                                node.getValue(),
                                i18nTransformerContext
                        );
                        boolean canModify = !((FieldDeclaration) parentParentNode).isStatic();
                        Optional<Node> parentParentParentOptional = parentParentNode.getParentNode();
                        if (parentParentParentOptional.isPresent() && parentParentParentOptional.get() instanceof ClassOrInterfaceDeclaration && ((ClassOrInterfaceDeclaration) parentParentParentOptional.get()).isInterface()) {
                            canModify = false;
                        }
                        if (!canModify) {
                            System.err.println("warn: change static field but still need manually handle: " + parentParentNode + " at file : " + i18nTransformerContext.getIdentifier());
                            variableDeclarator.setType(
                                    "java.util.function.Supplier<String>"
                            );
                            variableDeclarator.setName(
                                    variableDeclarator.getName() + "_SUPPLIER"
                            );
                            variableDeclarator.setInitializer(
                                    "() -> (" + handleResultString + ")"
                            );
                        } else {
                            variableDeclarator.setInitializer(
                                    handleResultString
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
            System.err.println("warn: EnumConstantDeclaration need manual change : " + parentNode + " at file : " + i18nTransformerContext.getIdentifier());
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
        } else if (parentNode instanceof BinaryExpr) {
            {
                Expression left = ((BinaryExpr) parentNode).getLeft();
                if (left instanceof StringLiteralExpr) {
                    if (PATTERN_CHINESE.matcher(((StringLiteralExpr) left).getValue()).matches()) {
                        ((BinaryExpr) parentNode).setLeft(
                                new NameExpr(
                                        handleString(
                                                ((StringLiteralExpr) left).getValue(),
                                                i18nTransformerContext
                                        )
                                )
                        );
                    }
                }
            }
            {
                Expression right = ((BinaryExpr) parentNode).getRight();
                if (right instanceof StringLiteralExpr) {
                    if (PATTERN_CHINESE.matcher(((StringLiteralExpr) right).getValue()).matches()) {
                        ((BinaryExpr) parentNode).setRight(
                                new NameExpr(
                                        handleString(
                                                ((StringLiteralExpr) right).getValue(),
                                                i18nTransformerContext
                                        )
                                )
                        );
                    }
                }
            }
        } else if (parentNode instanceof SingleMemberAnnotationExpr || parentNode instanceof MemberValuePair || parentNode instanceof AnnotationMemberDeclaration) {
            System.err.println("unhandled annotation : " + parentNode.getClass().getName() + " for node " + parentNode + " at file : " + i18nTransformerContext.getIdentifier());
        } else {
            System.err.println("unhandled class : " + parentNode.getClass().getName() + " for node " + parentNode + " at file : " + i18nTransformerContext.getIdentifier());
        }

    }

}
