package com.thrive.azure.servicebus.bug;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;

public class Config {
    public static String TOPIC = System.getenv("SERVICEBUS_TOPIC");
    public static String SUBSCRIPTION = System.getenv("SERVICEBUS_SUBSCRIPTION");
    public static String NAMESPACE = System.getenv("SERVICEBUS_NAMESPACE");
    public static String FULLY_QUALIFIED_NAMESPACE = NAMESPACE + ".servicebus.windows.net";

    public static TokenCredential CREDENTIAL = new DefaultAzureCredentialBuilder().build();

}
