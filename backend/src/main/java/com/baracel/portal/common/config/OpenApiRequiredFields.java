package com.baracel.portal.common.config;

import com.baracel.portal.common.web.Nullable;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Contract rule: every record component of an API type is required and not null, unless it is annotated
 * {@link Nullable}. Nullable components stay optional and also accept null.
 */
@Configuration
public class OpenApiRequiredFields {

    @Bean
    OpenApiCustomizer requiredFieldsCustomizer() {
        Map<String, Class<?>> records = apiRecords();
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                Class<?> type = records.get(name);
                if (type == null || schema.getProperties() == null) return;
                List<String> required = new ArrayList<>();
                for (RecordComponent c : type.getRecordComponents()) {
                    if (!schema.getProperties().containsKey(c.getName())) continue;
                    if (c.isAnnotationPresent(Nullable.class)) allowNull((Schema) schema.getProperties().get(c.getName()));
                    else required.add(c.getName());
                }
                schema.setRequired(required);   // an empty list is kept: all components optional
            });
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void allowNull(Schema property) {
        if (property.get$ref() != null) return;   // a nullable reference stays optional
        if (property.getTypes() != null && !property.getTypes().contains("null")) property.getTypes().add("null");
    }

    private static Map<String, Class<?>> apiRecords() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Map<String, Class<?>> out = new HashMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.baracel.portal")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                out.put(c.getSimpleName(), c);
            } catch (ClassNotFoundException ignored) { /* not loadable: skip */ }
        }
        return out;
    }
}
