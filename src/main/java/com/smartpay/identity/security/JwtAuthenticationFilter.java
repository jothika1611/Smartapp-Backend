package com.smartpay.identity.security;

import com.smartpay.identity.service.TokenRevocationService;
import com.smartpay.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Converts a valid, non-revoked Bearer token into a Spring Security authentication. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   CustomUserDetailsService userDetailsService,
                                   TokenRevocationService tokenRevocationService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        try {
            Claims claims = tokenProvider.parseAccessToken(token);
            String jti = claims.getId();

            if (tokenRevocationService.isRevoked(jti)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = Long.valueOf(claims.getSubject());
            CustomUserPrincipal principal = userDetailsService.loadUserById(userId);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(TokenRevocationService.JTI_ATTRIBUTE, jti);
            request.setAttribute(TokenRevocationService.ACCESS_TOKEN_ATTRIBUTE, token);
        } catch (JwtException | IllegalArgumentException | org.springframework.security.core.AuthenticationException ex) {
            // Invalid, expired, revoked or no-longer-active principal: leave the context empty
            // so the configured entry point returns a consistent 401 JSON response.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
