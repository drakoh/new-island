# New Island API

API to make reservations on a newly formed island

## Requirements
- java 14
- maven 3.6.x
- docker

## Quick Start

Start Postgres server:

```
docker rm -f dev-postgres
docker run --name dev-postgres -e POSTGRES_PASSWORD=password -p 5432:5432 -d postgres:12
docker exec dev-postgres psql -U postgres -c"CREATE DATABASE new_island" postgres
```

Start the app:

```
mvn clean install
cd app
mvn spring-boot:run
```

Go to url: `http://localhost:8080/new_island/docs.html` to see the api documentation and play with the service
