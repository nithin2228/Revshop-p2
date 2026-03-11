# 🛍️ RevShop — E-Commerce Web Application

A full-featured e-commerce platform built with **Spring Boot**, **Thymeleaf**, **Spring Security**, and **Oracle Database**. RevShop supports two user roles — **Buyers** and **Sellers** — with distinct dashboards, role-based access control, and a complete shopping workflow.

---

## 📋 Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Database Setup](#database-setup)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Running Tests](#running-tests)
- [API Overview](#api-overview)
- [Security](#security)

---

## ✨ Features

### Buyer
- Register and log in securely
- Browse and search products by category
- Add products to cart and manage quantities
- Place orders and view order history
- Add products to favorites/wishlist
- Write reviews for purchased products
- Forgot password recovery using security questions
- In-app notifications

### Seller
- Register with business name
- Seller dashboard for inventory management
- Add, update, and delete products with image uploads
- Manage product categories
- View orders placed for their products

### General
- BCrypt password hashing
- Role-based access control (BUYER / SELLER)
- Custom login success handler with role-based redirect
- Log4j2 structured logging
- Custom error pages (404, 500)

---

## 🛠️ Tech Stack

| Layer         | Technology                          |
|---------------|-------------------------------------|
| Backend       | Java 17, Spring Boot 4.0.3          |
| Web Framework | Spring MVC (WebMVC)                 |
| Templating    | Thymeleaf                           |
| Security      | Spring Security (BCrypt, Form Login)|
| Persistence   | Spring Data JPA / Hibernate         |
| Database      | Oracle XE (ojdbc11)                 |
| Logging       | Log4j2                              |
| Build Tool    | Maven                               |
| Testing       | JUnit 5, Mockito, Spring Boot Test  |

---

## 📁 Project Structure

```
revshop/
├── src/
│   ├── main/
│   │   ├── java/com/revshopproject/revshop/
│   │   │   ├── config/          # Security, Web, Logging configuration
│   │   │   ├── controller/      # MVC & REST controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── entity/          # JPA Entities
│   │   │   ├── exception/       # Custom exceptions & global handler
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── security/        # Custom UserDetailsService & login handler
│   │   │   ├── service/         # Service interfaces & implementations
│   │   │   └── utils/           # File upload utility
│   │   └── resources/
│   │       ├── templates/       # Thymeleaf HTML templates
│   │       ├── static/          # CSS, JS, images
│   │       ├── application.properties
│   │       └── log4j2-spring.xml
│   └── test/                    # Unit tests for all service layers
├── RevshopP2.sql                 # Database schema & seed data
└── pom.xml
```

---

## ✅ Prerequisites

- Java 17+
- Maven 3.8+
- Oracle Database XE (or compatible)
- An Oracle JDBC driver (`ojdbc11`) accessible via Maven

---

## 🚀 Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/revshop.git
cd revshop
```

### 2. Set up the database

See [Database Setup](#database-setup) below.

### 3. Configure application properties

See [Configuration](#configuration) below.

### 4. Build and run

```bash
./mvnw spring-boot:run
```

The application starts on **http://localhost:8888**

---

## 🗄️ Database Setup

Run the provided SQL script to create the schema and seed initial data:

```sql
-- Connect to Oracle as the appropriate user, then:
@RevshopP2.sql
```

Or using SQL*Plus:

```bash
sqlplus revshop_p2/revshop_p2@localhost:1521/xe @RevshopP2.sql
```

The script creates all required tables, sequences, and sample data.

---

## ⚙️ Configuration

Edit `src/main/resources/application.properties` to match your environment:

```properties
# Server
server.port=8888

# Oracle Database
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:xe
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.database-platform=org.hibernate.dialect.OracleDialect

# File Upload
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=30MB
```

> ⚠️ **Do not commit real credentials.** Use environment variables or a secrets manager in production.

---

## ▶️ Running the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or build a JAR and run it
./mvnw clean package -DskipTests
java -jar target/revshop-0.0.1-SNAPSHOT.jar
```

Navigate to **http://localhost:8888** in your browser.

---

## 🧪 Running Tests

```bash
./mvnw test
```

Unit tests are provided for all service layer classes using JUnit 5 and Mockito:

- `CartServiceImplTest`
- `CategoryServiceImplTest`
- `FavoriteServiceImplTest`
- `NotificationServiceImplTest`
- `OrderServiceImplTest`
- `ProductServiceImplTest`
- `ReviewServiceImplTest`
- `SellerServiceImplTest`
- `UserServiceImplTest`

---

## 🌐 API Overview

### Public Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Home / product listing |
| GET | `/product/{id}` | Product detail page |
| POST | `/api/users/register` | Register a new user |
| GET | `/api/products/**` | Browse products |
| GET | `/api/categories/**` | Browse categories |

### Buyer Endpoints *(requires BUYER role)*
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/cart/**` | View and manage cart |
| POST | `/api/orders/place` | Place an order |
| GET | `/api/orders/my-orders` | View order history |
| POST | `/api/reviews/**` | Submit a product review |
| GET/POST | `/api/favorites/**` | Manage wishlist |

### Seller Endpoints *(requires SELLER role)*
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/seller/**` | Seller dashboard data |
| POST | `/api/products/**` | Add a new product |
| PUT | `/api/products/**` | Update a product |
| DELETE | `/api/products/**` | Delete a product |
| POST | `/api/categories/**` | Add a category |

---

## 🔒 Security

- Passwords are encrypted using **BCryptPasswordEncoder**
- Session-based authentication via Spring Security form login
- Role-based authorization (`ROLE_BUYER`, `ROLE_SELLER`)
- Forgot password flow uses security questions
- CSRF protection enabled (with exceptions for specific public API endpoints)
- Unauthenticated API requests return `401 Unauthorized`; unauthenticated page requests redirect to `/login`

---

## 📄 License

This project was developed as part of a training/capstone project. Feel free to fork and adapt it for learning purposes.
