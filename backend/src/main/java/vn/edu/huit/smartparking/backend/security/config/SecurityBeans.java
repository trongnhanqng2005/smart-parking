package vn.edu.huit.smartparking.backend.security.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.huit.smartparking.backend.security.service.AccountAuthenticationProvider;
import vn.edu.huit.smartparking.backend.security.service.LoginAttemptThrottle;
import vn.edu.huit.smartparking.backend.security.service.SecurityBootstrapService;

@Configuration
@EnableConfigurationProperties({SecurityBootstrapProperties.class, JwtProperties.class})
public class SecurityBeans {
    @Bean
    Clock securityClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    LoginAttemptThrottle loginAttemptThrottle(Clock securityClock) {
        return new LoginAttemptThrottle(securityClock, 5, Duration.ofMinutes(15));
    }

    @Bean
    AuthenticationManager authenticationManager(AccountAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    @Profile("!test")
    ApplicationRunner securityBootstrap(SecurityBootstrapService bootstrapService) {
        return arguments -> bootstrapService.initialize();
    }

    @Bean
    @Profile("test")
    ApplicationRunner securityCatalogBootstrap(SecurityBootstrapService bootstrapService) {
        return arguments -> bootstrapService.initializeCatalog();
    }
}
