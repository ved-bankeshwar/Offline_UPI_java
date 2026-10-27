package com.demo.upimesh.web;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class ServletConfig {

    @Bean
    public ServletRegistrationBean<LedgerServlet> ledgerServletRegistration(LedgerServlet servlet) {
        ServletRegistrationBean<LedgerServlet> registration = new ServletRegistrationBean<>(servlet, "/ledger");
        registration.setName("ledgerServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public ServletRegistrationBean<BridgeIngestServlet> bridgeIngestServletRegistration(BridgeIngestServlet servlet) {
        ServletRegistrationBean<BridgeIngestServlet> registration =
                new ServletRegistrationBean<>(servlet, "/servlet/ingest");
        registration.setName("bridgeIngestServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    /** JSP files live in src/main/webapp. spring-boot:run is started from the project directory. */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> jspDocumentRoot() {
        return factory -> {
            factory.addContextCustomizers(context -> context.setSessionTimeout(30));
            File webapp = new File("src/main/webapp");
            if (webapp.isDirectory()) {
                factory.setDocumentRoot(webapp);
            }
        };
    }
}
