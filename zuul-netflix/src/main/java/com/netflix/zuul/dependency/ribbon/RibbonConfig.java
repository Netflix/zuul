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
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.zuul.ZuulApplicationInfo;
import com.netflix.zuul.constants.ZuulConstants;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Eureka VIP names and addresses.
 *
 * @author mhawthorne
 */
public class RibbonConfig {


    static String APPLICATION_NAME = null;

    static String APPLICATION_STACK = null;

    private static Logger LOG = LoggerFactory.getLogger(RibbonConfig.class);


    private static final DynamicBooleanProperty AUTODETECT_BACKEND_VIPS =
            DynamicPropertyFactory.getInstance().getBooleanProperty(ZuulConstants.ZUUL_AUTODETECT_BACKEND_VIPS, true);
    private static final DynamicStringProperty DEFAULT_CLIENT =
            DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_NIWS_DEFAULTCLIENT, null);

    /**
     * This method attempts to set up the default Ribbon origin VIP from properties and environment.
     * One method is through autoscale name convention. The Autoscaling group name can be set up as follow : zuul-origin_stack.
     * Zuul will derive the origin VIP  as origin-stack.{zuul.ribbon.vipAddress.template}
     * <p/>
     * the client may also be specified by the property ZuulConstants.ZUUL_NIWS_DEFAULTCLIENT
     *
     * @throws ClientException
     */
    public static void setupDefaultRibbonConfig() throws ClientException {
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
        String vip = RibbonConfig.getDefaultVipName();
        String vipAddr = RibbonConfig.getDefaultVipAddress(getApplicationStack());
        String namespace = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_RIBBON_NAMESPACE, "ribbon").get();

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

    /**
     * @return the APPLICATION_NAME
     */
    public static String getApplicationName() {
        return APPLICATION_NAME;
    }

    /**
     * sets the application name of the origin
     *
     * @param app_name
     */
    public static void setApplicationName(String app_name) {
        RibbonConfig.APPLICATION_NAME = app_name;
        if (ZuulApplicationInfo.applicationName == null) ZuulApplicationInfo.applicationName = app_name;
        LOG.info("Setting back end VIP application = " + app_name);
    }

    /**
     * returns the application_stack
     *
     * @return
     */
    public static String getApplicationStack() {
        return APPLICATION_STACK;
    }

    /**
     * sets the default origin applcation stack
     *
     * @param stack
     */
    public static void setApplicationStack(String stack) {
        RibbonConfig.APPLICATION_STACK = stack;
        if (ZuulApplicationInfo.getStack() == null) ZuulApplicationInfo.stack = stack;
        LOG.info("Setting back end VIP stack = " + stack);
    }

    /**
     * true if the app shoudl autodetect the origin vip
     *
     * @return true if the app shoudl autodetect the origin vip
     */
    public static final boolean isAutodetectingBackendVips() {
        return AUTODETECT_BACKEND_VIPS.get();
    }

    /**
     * returns the Ribbon property name for the default origin vip
     *
     * @return
     */
    public static final String getDefaultVipName() {
        String client = getApplicationName();
        if (client == null) client = DEFAULT_CLIENT.get();

        String namespace = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_RIBBON_NAMESPACE, "ribbon").get();

        String vipTemplate = "%s." + namespace + ".DeploymentContextBasedVipAddresses";
        return String.format(vipTemplate, client);
    }


    /**
     * builds the default vip address of the origin based on the stack.
     * You need to configure zuul.ribbon.vipAddress.template . eg zuul.ribbon.vipAddress.template=%s-%s.netflix.net:8888 where %s(1) is client and %s(2) is stack
     *
     * @param stack
     * @return
     */
    public static final String getDefaultVipAddress(String stack) {
        String client = getApplicationName();
        if (client == null) client = DEFAULT_CLIENT.get();

        String vipAddressTemplate = DynamicPropertyFactory.getInstance().getStringProperty(ZuulConstants.ZUUL_RIBBON_VIPADDRESS_TEMPLATE, null).get();
        if (vipAddressTemplate == null)
            throw new RuntimeException("need to configure zuul.ribbon.vipAddress.template . eg zuul.ribbon.vipAddress.template=%s-%s.netflix.net:8888 where %s(1) is client and %s(2) is stack");

        return String.format(vipAddressTemplate, client, stack);
    }
}
