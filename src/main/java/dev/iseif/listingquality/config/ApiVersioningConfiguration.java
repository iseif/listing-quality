package dev.iseif.listingquality.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ApiVersioningConfiguration implements WebMvcConfigurer {

  private static final String API_VERSION_HEADER = "X-API-Version";
  private static final String VERSION_1 = "1";

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer
        .useRequestHeader(API_VERSION_HEADER)
        .addSupportedVersions(VERSION_1)
        .setDefaultVersion(VERSION_1);
  }
}
