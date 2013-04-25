/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.dependency.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.*;
import com.netflix.zuul.ZuulApplicationInfo;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/**
 * Handles NIWS VIP names and addresses.
 *
 * @author mhawthorne
 */
public class NIWSConfig {


    static String APPLICATION_NAME = null;

    static String APPLICATION_STACK = null;

    private static Logger LOG = LoggerFactory.getLogger(NIWSConfig.class);


    private static final DynamicBooleanProperty AUTODETECT_BACKEND_VIPS =
            DynamicPropertyFactory.getInstance().getBooleanProperty("zuul.autodetect-backend-vips", true);
    private static final DynamicStringProperty DEFAULT_CLIENT =
            DynamicPropertyFactory.getInstance().getStringProperty("zuul.niws.defaultClient", null);


    public static void setupDefaultNIWSConfig() throws ClientException {
        final DeploymentContext config = ConfigurationManager.getDeploymentContext();

        String stack = config.getDeploymentStack();

        if (stack != null && stack.contains("_")) { //use stack for client and stack  client_stack
            setAppInfoFromZuulStack(stack);
        } else {
            String env = config.getDeploymentEnvironment();
            if (stack != null) {
                setApplicationName(stack);
            } else {
                if (DEFAULT_CLIENT.get() == null)
                    throw new RuntimeException("Can't figure out default origin vips. Set stack as appName_stack of set zuul.niws.defaultClient param");
                setApplicationName(DEFAULT_CLIENT.get());
            }

            setApplicationStack(env);
        }
        String vip = NIWSConfig.getDefaultVipName();
        String vipAddr = NIWSConfig.getDefaultVipAddress(getApplicationStack());
        String namespace = DynamicPropertyFactory.getInstance().getStringProperty("zuul.ribbon.namespace", "ribbon").get();
        
        setIfNotDefined(vip, vipAddr);
        setIfNotDefined(getApplicationName() + "." + namespace + ".Port", "7001");
        setIfNotDefined(getApplicationName() + "." + namespace + ".AppName", getApplicationName());
        setIfNotDefined(getApplicationName() + "." + namespace + ".ReadTimeout", "2000");
        setIfNotDefined(getApplicationName() + "." + namespace + ".ConnectTimeout", "2000");
        setIfNotDefined(getApplicationName() + "." + namespace + ".MaxAutoRetriesNextServer", "1");
        setIfNotDefined(getApplicationName() + "." + namespace + ".FollowRedirects", "false");
        setIfNotDefined(getApplicationName() + "." + namespace + ".ConnIdleEvictTimeMilliSeconds", "3600000");
        setIfNotDefined(getApplicationName() + "." + namespace + ".EnableZoneAffinity", "true");
        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues(getApplicationName(),
                namespace);
        ClientFactory.registerClientFromProperties(getApplicationName(), clientConfig);
    }

    private static void setIfNotDefined(String key, String value) {
        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        if (config.getString(key) == null) {
            LOG.info("Setting default NIWS Property " + key + "=" + value);
            config.setProperty(key, value);
        }
    }


    /**
     * Parses out the the applicationName and application stack from the zuul "stack" element.
     * when deployed, the applicaiton
     *
     * @param stack
     * @return
     */
    public static boolean setAppInfoFromZuulStack(String stack) {
        String[] stackSplit = stack.split("_");
        if (stackSplit == null || stackSplit.length != 2) return false;

        setApplicationName(stackSplit[0]);
        setApplicationStack(stackSplit[1]);
        return true;
    }

    public static String getApplicationName() {
        return APPLICATION_NAME;
    }

    public static void setApplicationName(String app_name) {
        NIWSConfig.APPLICATION_NAME = app_name;
        if(ZuulApplicationInfo.applicationName == null) ZuulApplicationInfo.applicationName = app_name;
        LOG.info("Setting back end VIP application = " + app_name);
    }

    public static String getApplicationStack() {
        return APPLICATION_STACK;
    }

    public static void setApplicationStack(String stack) {
        NIWSConfig.APPLICATION_STACK = stack;
        if(ZuulApplicationInfo.getStack()== null) ZuulApplicationInfo.stack= stack;
        LOG.info("Setting back end VIP stack = " + stack);
    }


    public static final boolean isAutodetectingBackendVips() {
        return AUTODETECT_BACKEND_VIPS.get();
    }

    public static final String getDefaultVipName() {
        String client = getApplicationName();
        if (client == null) client = DEFAULT_CLIENT.get();

        String namespace = DynamicPropertyFactory.getInstance().getStringProperty("zuul.ribbon.namespace", "ribbon").get();

        String vipTemplate = "%s."+ namespace +".DeploymentContextBasedVipAddresses";
        return String.format(vipTemplate, client);
    }


    public static final String getDefaultVipAddress(String stack) {
        String client = getApplicationName();
        if (client == null) client = DEFAULT_CLIENT.get();

        String vipAddressTemplate = DynamicPropertyFactory.getInstance().getStringProperty("zuul.ribbon.vipAddress.template", null).get();
        if(vipAddressTemplate == null) throw new RuntimeException("need to configure zuul.ribbon.vipAddress.template . eg zuul.ribbon.vipAddress.template=%s-%s.netflix.net:8888 where %s(1) is client and %s(2) is stack" );

        return String.format(vipAddressTemplate, client, stack);
    }


    public static final class UnitTest {

        @Test
        public void defaultVipAddressForStandardStack() {
            //todo fix
//            assertEquals("null-prod.netflix.net:7001", NIWSConfig.getDefaultVipAddress("prod"));
        }


        @Test
        public void defaultVipAddressForLatAmStack() {
            //todo fix
//            assertEquals("null-prod.latam.netflix.net:7001", NIWSConfig.getDefaultVipAddress("prod.latam"));
        }


    }

}
