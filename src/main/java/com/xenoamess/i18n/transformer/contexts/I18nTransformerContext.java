package com.xenoamess.i18n.transformer.contexts;

import com.xenoamess.i18n.transformer.entities.PropertiesEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class I18nTransformerContext {

    @NotNull
    private String i18nTemplate;

    @NotNull
    private String propertyBundleName;

    @NotNull
    private String identifier;

    @Nullable
    private String prefixKey;

    private int currentIndex = 0;

    @NotNull
    private List<PropertiesEntity> chinesePropertiesEntities = new ArrayList<>();

}
