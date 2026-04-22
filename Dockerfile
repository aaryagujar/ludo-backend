# Use Java 17
FROM eclipse-temurin:17-jdk

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Build the project
RUN mvn clean package -DskipTests
# Run the jar file
CMD ["java", "-jar", "target/ludo-game-backend-1.0.0.jar"]