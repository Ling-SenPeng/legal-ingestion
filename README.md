# Legal Ingestion

A simple Java Hello World program built with Maven.

## Requirements

- **Java 17**
- **Maven 3.9+**

## 🛠️ Setup Instructions

### Prerequisites

1. **Java 17**
   - Verify installation: `java -version`
   - Should show Java 17.x.x

2. **Maven**
   - Download and install from [maven.apache.org](https://maven.apache.org/install.html)
   - Verify installation: `mvn --version`

### Build and Run

1. **Build the project**
   ```bash
   mvn clean package
   ```

2. **Run the application**
   ```bash
   java -cp target/legal-ingestion-0.0.1-SNAPSHOT.jar com.ingestion.HelloWorld
   ```
   Or run from the JAR file:
   ```bash
   java -jar target/legal-ingestion-0.0.1-SNAPSHOT.jar
   ```

## Project Structure

```
legal-ingestion/
├── src/
│   ├── main/
│   │   ├── java/com/ingestion/
│   │   │   └── HelloWorld.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/ingestion/
│           └── HelloWorldTest.java
├── pom.xml
└── README.md
```

## Development

### Building the Project
```bash
mvn clean package
```

### Running Tests
```bash
mvn test
```

### Running Directly
```bash
mvn compile exec:java -Dexec.mainClass="com.ingestion.HelloWorld"
```

## License

Apache-2.0 License
