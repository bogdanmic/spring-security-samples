package com.jdriven.flights;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.thymeleaf.extras.springsecurity5.dialect.SpringSecurityDialect;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.headers().frameOptions().sameOrigin();
		http.authorizeRequests().anyRequest().authenticated();
		http.oauth2ResourceServer().jwt().jwtAuthenticationConverter(jwtAuthenticationConverter());
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		final JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
		jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
		return jwtAuthenticationConverter;
	}

	@Bean
	public JwtDecoder jwtDecoderByIssuerUri(OAuth2ResourceServerProperties properties) {
		String issuerUri = properties.getJwt().getIssuerUri();
		NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
		jwtDecoder.setClaimSetConverter(new UsernameSubClaimAdapter());
		return jwtDecoder;
	}

	@Bean
	public SpringSecurityDialect springSecurityDialect() {
		return new SpringSecurityDialect();
	}

}

// As per: https://docs.spring.io/spring-security/site/docs/5.2.x/reference/html5/#oauth2resourceserver-jwt-claimsetmapping-rename
class UsernameSubClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {

	private final MappedJwtClaimSetConverter delegate = MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

	@Override
	public Map<String, Object> convert(Map<String, Object> claims) {
		Map<String, Object> convertedClaims = this.delegate.convert(claims);
		String username = (String) convertedClaims.get("preferred_username");
		convertedClaims.put("sub", username);
		return convertedClaims;
	}

}

class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	@Override
	@SuppressWarnings("unchecked")
	public Collection<GrantedAuthority> convert(final Jwt jwt) {
		final Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
		return ((List<String>) realmAccess.get("roles")).stream()
				.map(roleName -> "ROLE_" + roleName)
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
	}

}