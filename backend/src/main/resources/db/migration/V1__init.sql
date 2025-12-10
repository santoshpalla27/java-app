CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE connectivity_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target VARCHAR(50) NOT NULL, -- MYSQL, REDIS, KAFKA
    status VARCHAR(20) NOT NULL, -- SUCCESS, FAILURE, DEGRADED
    latency_ms BIGINT NOT NULL,
    error_message TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE service_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL, -- HEALTHY, DEGRADED, FAILED
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE deployment_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL, -- STARTED, COMPLETED, FAILED
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default admin user (password: admin123)
-- BCrypt encoded: $2a$10$wWw...
INSERT INTO users (username, password, role) VALUES ('admin', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmjMaJJJwal3v1tJSk4C', 'ADMIN');
