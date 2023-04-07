package com.xenoamess.i18n;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

@AAA("我是注解")
@AllArgsConstructor
enum EnumA {
    A("值aaa"),
    B("值bbb");

    @Getter
    private final String value;
}

interface IA {

    final String value = "接口中文";

}

interface IB {

    interface IC {
        final String value = "接口中文2";
    }

}

public class Main {

    private static final String STATIC_STRING_NAME = "名称";

    private final String STRING_NAME = "名称";

    public static void main(String[] args) {
        String a = "一个字符串";
        System.out.println("你好世界!");
        Arrays.asList("数组1", "数组2", "数组3");
        System.out.println(
                new String[]{
                        "你好世界数组1!",
                        "a",
                        "你好世界数组2!",
                        "b",
                        "你好世界数组3!",
                        "c"
                }
        );
        Pair<String, String> pair = new Pair<>("键", "值");

    }

    public static class MainInner {

    }

}