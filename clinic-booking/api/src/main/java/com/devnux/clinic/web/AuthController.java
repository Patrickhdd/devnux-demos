package com.devnux.clinic.web;

import com.devnux.clinic.service.AuthService;
import com.devnux.clinic.web.Dtos.LoginRequest;
import com.devnux.clinic.web.Dtos.RegisterRequest;
import com.devnux.clinic.web.Dtos.SessionView;
import com.devnux.clinic.web.Dtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView register(@Valid @RequestBody RegisterRequest req) {
        return view(auth.register(req.email(), req.password(), req.fullName()));
    }

    @PostMapping("/login")
    public SessionView login(@Valid @RequestBody LoginRequest req) {
        return view(auth.login(req.email(), req.password()));
    }

    private static SessionView view(AuthService.Session s) {
        return new SessionView(s.token(), s.expiresAt(), UserView.of(s.user()));
    }
}
