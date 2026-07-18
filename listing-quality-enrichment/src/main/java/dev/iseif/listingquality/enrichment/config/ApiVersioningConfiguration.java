package dev.iseif.listingquality.enrichment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiVersioningConfiguration implements WebMvcConfigurer {

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer
        .useRequestHeader("X-API-Version")
        .addSupportedVersions("1")
        .setDefaultVersion("1");
  }
}
