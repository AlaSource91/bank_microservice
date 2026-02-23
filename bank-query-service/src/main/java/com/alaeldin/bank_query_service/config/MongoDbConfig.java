package com.alaeldin.bank_query_service.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB Configuration.
 * Explicitly configures MongoDB client, database factory, and template
 * to avoid conflicts with Redis repositories.
 */
@Configuration
@EnableMongoRepositories(
        basePackages = "com.alaeldin.bank_query_service.repository",
        considerNestedRepositories = true
)
public class MongoDbConfig {

    @Value("${spring.data.mongodb.host:localhost}")
    private String mongoHost;

    @Value("${spring.data.mongodb.port:27017}")
    private int mongoPort;

    @Value("${spring.data.mongodb.database:bank_query_db}")
    private String mongoDatabase;

    @Value("${spring.data.mongodb.username:admin}")
    private String mongoUsername;

    @Value("${spring.data.mongodb.password:admin123}")
    private String mongoPassword;

    @Value("${spring.data.mongodb.authentication-database:admin}")
    private String authDatabase;

    /**
     * Creates a MongoClient bean for connecting to MongoDB.
     * Uses credentials from application.yaml for authentication.
     *
     * @return configured MongoClient instance
     */
    @Bean
    public MongoClient mongoClient() {
        // Build connection string with authentication
        String connectionString;
        if (mongoUsername != null && !mongoUsername.isEmpty()) {
            connectionString = String.format("mongodb://%s:%s@%s:%d/%s?authSource=%s",
                    mongoUsername, mongoPassword, mongoHost, mongoPort, mongoDatabase, authDatabase);
        } else {
            connectionString = String.format("mongodb://%s:%d/%s", mongoHost, mongoPort, mongoDatabase);
        }

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .build();

        return MongoClients.create(settings);
    }

    /**
     * Creates a MongoDatabaseFactory bean using the MongoClient.
     * This factory is used by MongoTemplate to access the MongoDB database.
     *
     * @return configured MongoDatabaseFactory instance
     */
    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory() {
        return new SimpleMongoClientDatabaseFactory(mongoClient(), mongoDatabase);
    }

    /**
     * Creates a MongoTemplate bean for MongoDB operations.
     * This bean is required by Spring Data MongoDB for repository operations.
     *
     * @return configured MongoTemplate instance
     */
    @Bean
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongoDatabaseFactory());
    }
}

