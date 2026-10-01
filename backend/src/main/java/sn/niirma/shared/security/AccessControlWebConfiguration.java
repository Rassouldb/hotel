package sn.niirma.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class AccessControlWebConfiguration implements WebMvcConfigurer {

    private final AccessControl accessControl;

    AccessControlWebConfiguration(AccessControl accessControl) {
        this.accessControl = accessControl;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessControlInterceptor(accessControl));
    }
}
