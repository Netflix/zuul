package com.netflix.zuul;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.*;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Handles NIWS VIP names and addresses.
 *
 * @author mhawthorne
 */
public class NIWSConfig {


    static String APPLICATION_NAME = null;

    static String APPLICATION_STACK = null;


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
                    throw new RuntimeException("Can't figure out default proxy vips. Set stack as appName_stack of set zuul.niws.defaultClient param");
                setApplicationName(DEFAULT_CLIENT.get());
            }

            setApplicationStack(env);
        }
        String vip = NIWSConfig.getDefaultVipName();
        String vipAddr = NIWSConfig.getDefaultVipAddress(getApplicationStack());
        setIfNotDefined(vip, vipAddr);
        setIfNotDefined(getApplicationName() + ".niws.client.Port", "7001");
        setIfNotDefined(getApplicationName() + ".niws.client.AppName", getApplicationName());
        setIfNotDefined(getApplicationName() + ".niws.client.ReadTimeout", "2000");
        setIfNotDefined(getApplicationName() + ".niws.client.ConnectTimeout", "2000");
        setIfNotDefined(getApplicationName() + ".niws.client.MaxAutoRetriesNextServer", "1");
        setIfNotDefined(getApplicationName() + ".niws.client.FollowRedirects", "false");
        setIfNotDefined(getApplicationName() + ".niws.client.ConnIdleEvictTimeMilliSeconds", "3600000");
        setIfNotDefined(getApplicationName() + ".niws.client.EnableZoneAffinity", "true");
        DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues(getApplicationName());
        ClientFactory.registerClientFromProperties(getApplicationName(), clientConfig);
    }

    private static void setIfNotDefined(String key, String value) {
        final AbstractConfiguration config = ConfigurationManager.getConfigInstance();
        if (config.getString(key) == null) {
            System.out.println("Setting default NIWS Property " + key + "=" + value);
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
        System.out.println("Setting back end VIP application = " + app_name);
    }

    public static String getApplicationStack() {
        return APPLICATION_STACK;
    }

    public static void setApplicationStack(String stack) {
        NIWSConfig.APPLICATION_STACK = stack;
        if(ZuulApplicationInfo.getStack()== null) ZuulApplicationInfo.stack= stack;

        System.out.println("Setting back end VIP stack = " + stack);

    }

    // derives backend VIP addresses from stack - uses defaults from properties files if stack is unavailable
    // (stack is null when running locally)
    private static final String VIP_NAME_TEMPLATE = "%s.niws.client.DeploymentContextBasedVipAddresses";
    private static final String VIP_ADDRESS_TEMPLATE = "%s-%s.netflix.net:7001";

    public static final boolean isAutodetectingBackendVips() {
        return AUTODETECT_BACKEND_VIPS.get();
    }

    public static final String getDefaultVipName() {
        String client = getApplicationName();
        if (client == null) client = DEFAULT_CLIENT.get();

        return String.format(VIP_NAME_TEMPLATE, client);
    }


    public static final String getDefaultVipAddress(String stack) {
        String client = getApplicationName();
        if (client == null) client = DEFAULT_CLIENT.get();

        return String.format(VIP_ADDRESS_TEMPLATE, client, stack);
    }


    public static final class UnitTest {

        @Test
        public void defaultVipAddressForStandardStack() {
            assertEquals("null-prod.netflix.net:7001", NIWSConfig.getDefaultVipAddress("prod"));
        }


        @Test
        public void defaultVipAddressForLatAmStack() {
            assertEquals("null-prod.latam.netflix.net:7001", NIWSConfig.getDefaultVipAddress("prod.latam"));
        }


    }

}
