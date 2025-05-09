// src/main/java/com/aasx/transformer/config/Aas4jJsonConfig.java
package com.aasx.transformer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonMapperFactory;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.SimpleAbstractTypeResolverFactory;

@Configuration
public class Aas4jJsonConfig {
  @Bean
  @Primary
  public ObjectMapper aas4jObjectMapper() {
    var typeResolver = new SimpleAbstractTypeResolverFactory().create();
    return new JsonMapperFactory().create(typeResolver);
  }
}
