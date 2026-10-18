CREATE DATABASE fileshare
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE fileshare;

CREATE TABLE users (
                       id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                       username VARCHAR(50) NOT NULL,
                       email VARCHAR(255) NOT NULL,
                       password VARCHAR(255) NOT NULL,

                       PRIMARY KEY (id),
                       UNIQUE KEY username (username),
                       UNIQUE KEY email (email)
) ENGINE=InnoDB;

CREATE TABLE files (
                       id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                       file_name VARCHAR(255) NOT NULL,
                       size BIGINT UNSIGNED NOT NULL,
                       owner_id INT UNSIGNED NOT NULL,
                       uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),
                       KEY files_ibfk_1 (owner_id),
                       CONSTRAINT files_ibfk_1
                           FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE file_shares (
                             file_id BIGINT UNSIGNED NOT NULL,
                             user_id INT UNSIGNED NOT NULL,

                             PRIMARY KEY (file_id, user_id),
                             KEY user_id (user_id),
                             CONSTRAINT file_shares_ibfk_1
                                 FOREIGN KEY (file_id) REFERENCES files (id)
                                     ON DELETE CASCADE,
                             CONSTRAINT file_shares_ibfk_2
                                 FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE USER 'fileshare_admin'@'localhost'
    IDENTIFIED BY '581039';

GRANT ALL PRIVILEGES ON fileshare.*
    TO 'fileshare_admin'@'localhost';