package com.netflix.zuul.guice;

import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AWSModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AWSModule.class);
    private static final String AWS_DYNAMO_DB_REGION = DynamicPropertyFactory.getInstance().getStringProperty("aws.dynamodb.region", "").get();
    private static final String AWS_DYNAMO_DB_ENDPOINT = DynamicPropertyFactory.getInstance().getStringProperty("aws.dynamodb.endpoint", "").get();

    @Override
    protected void configure() {
    }

    private AWSCredentialsProviderChain getAwsCredentialsProviderChain() {
        return new AWSCredentialsProviderChain(
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                new InstanceProfileCredentialsProvider()
        );
    }

    @Provides
    @Singleton
    private AmazonDynamoDB getAmazonDynamoDBClient() {
        final AmazonDynamoDB dynamoDbClient = new AmazonDynamoDBClient(getAwsCredentialsProviderChain());
        if(!AWS_DYNAMO_DB_REGION.isEmpty()) {
            dynamoDbClient.setRegion(Region.getRegion(Regions.fromName(AWS_DYNAMO_DB_REGION)));
        }
        if(!AWS_DYNAMO_DB_ENDPOINT.isEmpty()) {
            dynamoDbClient.setEndpoint(AWS_DYNAMO_DB_ENDPOINT);
        }
        return dynamoDbClient;
    }


}
