# Books Online Microservices

Event-driven Spring Boot microservices for an online books ordering system.

## Services

- `catalog-service`
  - Exposes `GET`, `POST`, `PUT`, and `DELETE` on `/api/books`
  - Exposes `GET /api/books/metadata/columns` for frontend table and form metadata
  - Reads and writes the existing PostgreSQL `books` table using JDBC
  - Caches book responses in Redis for lower database load
- `cart-service`
  - Exposes shopping cart APIs on `/api/carts/{customerId}`
  - Stores cart state in Redis for fast access and horizontal scaling
  - Publishes checkout events to Kafka
- `order-service`
  - Consumes checkout events from Kafka
  - Persists orders and order lines in PostgreSQL
  - Exposes `GET /api/orders/{customerId}`
- `books-ui`
  - Angular frontend for CRUD operations against the `books` table
  - Uses the catalog service REST API directly from the browser

## Infrastructure Configuration

Configured defaults match your environment:

- PostgreSQL: `192.168.1.88:5435/bookstoreonline`
- Redis: `192.168.1.88:30073`
- Kafka: defaults to `192.168.1.88:9092` and can be overridden with `KAFKA_BOOTSTRAP_SERVERS`

## Run

Build everything:

```bash
mvn clean package
```

Run each service:

```bash
mvn -pl catalog-service spring-boot:run
mvn -pl cart-service spring-boot:run
mvn -pl order-service spring-boot:run
```

Run the Angular UI:

```bash
cd books-ui
npm start
```

## Example API Flow

List books:

```bash
curl http://localhost:8081/api/books
```

Create a book:

```bash
curl -X POST http://localhost:8081/api/books \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"New Book\",\"isbn\":\"1234567890\",\"price\":29.99}"
```

Add a book to cart:

```bash
curl -X POST http://localhost:8082/api/carts/customer-100/items \
  -H "Content-Type: application/json" \
  -d "{\"bookId\":1,\"quantity\":2}"
```

Checkout cart:

```bash
curl -X POST http://localhost:8082/api/carts/customer-100/checkout
```

Read created orders:

```bash
curl http://localhost:8083/api/orders/customer-100
```
