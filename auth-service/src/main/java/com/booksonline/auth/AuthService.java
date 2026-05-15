package com.booksonline.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final int TOKEN_TTL_HOURS = 24;

    private final JdbcClient jdbcClient;
    private final PasswordHasher passwordHasher;

    public AuthService(JdbcClient jdbcClient, PasswordHasher passwordHasher) {
        this.jdbcClient = jdbcClient;
        this.passwordHasher = passwordHasher;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailNotTaken(email);

        Long userId = jdbcClient.sql("""
                insert into auth_users (email, full_name, password_hash)
                values (:email, :fullName, :passwordHash)
                returning id
                """)
                .param("email", email)
                .param("fullName", request.fullName().trim())
                .param("passwordHash", passwordHasher.hash(request.password()))
                .query(Long.class)
                .single();

        AuthUser user = new AuthUser(userId, email, request.fullName().trim());
        String token = createToken(userId);
        return new AuthResponse(token, user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        List<Map<String, Object>> rows = jdbcClient.sql("""
                select id, email, full_name, password_hash
                from auth_users
                where email = :email
                """)
                .param("email", email)
                .query()
                .listOfRows();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        Map<String, Object> row = rows.getFirst();
        String passwordHash = String.valueOf(row.get("password_hash"));
        if (!passwordHasher.matches(request.password(), passwordHash)) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        Long userId = ((Number) row.get("id")).longValue();
        AuthUser user = new AuthUser(
                userId,
                String.valueOf(row.get("email")),
                String.valueOf(row.get("full_name"))
        );

        String token = createToken(userId);
        return new AuthResponse(token, user);
    }

    public AuthUser getCurrentUser(String authorization) {
        String token = extractBearerToken(authorization);
        List<Map<String, Object>> rows = jdbcClient.sql("""
                select u.id, u.email, u.full_name
                from auth_tokens t
                join auth_users u on u.id = t.user_id
                where t.token = :token
                  and t.expires_at > current_timestamp
                """)
                .param("token", token)
                .query()
                .listOfRows();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Authentication token is invalid or expired");
        }

        Map<String, Object> row = rows.getFirst();
        return new AuthUser(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("email")),
                String.valueOf(row.get("full_name"))
        );
    }

    public void logout(String authorization) {
        String token = extractBearerToken(authorization);
        jdbcClient.sql("delete from auth_tokens where token = :token")
                .param("token", token)
                .update();
    }

    private void ensureEmailNotTaken(String email) {
        Integer count = jdbcClient.sql("select count(*) from auth_users where email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();

        if (count != null && count > 0) {
            throw new IllegalArgumentException("An account with that email already exists");
        }
    }

    private String createToken(Long userId) {
        String token = UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");
        Timestamp expiresAt = Timestamp.valueOf(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS));

        jdbcClient.sql("""
                insert into auth_tokens (token, user_id, expires_at)
                values (:token, :userId, :expiresAt)
                """)
                .param("token", token)
                .param("userId", userId)
                .param("expiresAt", expiresAt)
                .update();

        return token;
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
