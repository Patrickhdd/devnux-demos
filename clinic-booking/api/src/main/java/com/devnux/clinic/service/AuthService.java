package com.devnux.clinic.service;

import com.devnux.clinic.config.ClinicProperties;
import com.devnux.clinic.domain.AppUser;
import com.devnux.clinic.domain.Role;
import com.devnux.clinic.repo.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record Session(String token, Instant expiresAt, AppUser user) {}

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final ClinicProperties props;
    private final Clock clock;

    public AuthService(AppUserRepository users, PasswordEncoder passwords, JwtEncoder jwtEncoder,
                       ClinicProperties props, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public Session register(String email, String password, String fullName) {
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists");
        AppUser user = users.save(new AppUser(email, passwords.encode(password), fullName, Role.PATIENT));
        return issue(user);
    }

    @Transactional(readOnly = true)
    public Session login(String email, String password) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .filter(u -> passwords.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        return issue(user);
    }

    private Session issue(AppUser user) {
        Instant now = clock.instant();
        Instant expires = now.plus(props.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("clinic-booking")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Session(token, expires, user);
    }
}
