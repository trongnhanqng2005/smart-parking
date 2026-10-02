package vn.edu.huit.smartparking.backend.security.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import vn.edu.huit.smartparking.backend.security.service.CurrentJwtAuthenticationConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    private static final String WEB_CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self'",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-src 'none'",
            "frame-ancestors 'none'");

    @Bean
    FilterRegistrationBean<WebSessionSecurityFilter> webSessionFilterRegistration(WebSessionSecurityFilter filter) {
        FilterRegistrationBean<WebSessionSecurityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain restSecurityFilterChain(
            HttpSecurity http,
            CurrentJwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            WebAuthenticationSuccessHandler successHandler,
            WebAuthenticationFailureHandler failureHandler,
            WebLogoutAuditHandler logoutSuccessHandler,
            WebSessionSecurityFilter sessionSecurityFilter,
            AuthRequestDetailsSource detailsSource) throws Exception {
        http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/assets/**").permitAll()
                        .requestMatchers("/").hasRole("MANAGEMENT")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .authenticationDetailsSource(detailsSource)
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .csrf(withDefaults())
                .headers(headers -> headers.contentSecurityPolicy(
                        csp -> csp.policyDirectives(WEB_CONTENT_SECURITY_POLICY)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            String requestPath = request.getRequestURI().substring(request.getContextPath().length());
                            if (HttpMethod.GET.matches(request.getMethod()) && "/".equals(requestPath)) {
                                response.sendRedirect(request.getContextPath() + "/login");
                            } else {
                                response.sendError(401);
                            }
                        })
                        .accessDeniedHandler((request, response, exception) -> response.sendError(403)))
                .addFilterAfter(sessionSecurityFilter, SecurityContextHolderFilter.class);
        return http.build();
    }
}
