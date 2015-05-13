package com.netflix.zuul.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.governator.annotations.WarmUp;
import com.netflix.zuul.ZuulApplicationInfo;
import com.netflix.zuul.dependency.ribbon.RibbonConfig;
import com.netflix.zuul.exception.ZuulException;
import com.netflix.zuul.origins.Origin;
import com.netflix.zuul.origins.OriginManager;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.netflix.zuul.constants.ZuulConstants.*;

/**
 * User: Mike Smith
 * Date: 5/12/15
 * Time: 11:24 PM
 */
@Singleton
public class RibbonOriginManager implements OriginManager
{
    private final Map<String, Origin> origins = new ConcurrentHashMap<>();

    @WarmUp
    public void initialize()
    {
        try {
            String stack = ConfigurationManager.getDeploymentContext().getDeploymentStack();

            // Setup the default origin.
            String defaultClientName;
            if (stack != null && !stack.trim().isEmpty() && RibbonConfig.isAutodetectingBackendVips()) {
                defaultClientName = RibbonConfig.setupDefaultRibbonConfig();

            } else {
                DynamicStringProperty DEFAULT_CLIENT = DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_NIWS_DEFAULTCLIENT, null);
                defaultClientName = DEFAULT_CLIENT.get();
                if (defaultClientName == null) {
                    defaultClientName = stack;
                }
                ZuulApplicationInfo.setApplicationName(defaultClientName);
            }
            origins.put(defaultClientName, new RibbonOrigin(defaultClientName));

            // Setup the other configured origins.
            String clientPropertyList = DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_NIWS_CLIENTLIST, "").get();
            String[] aClientList = clientPropertyList.split("\\|");
            String namespace = DynamicPropertyFactory.getInstance().getStringProperty(ZUUL_RIBBON_NAMESPACE, "ribbon").get();
            for (String client : aClientList) {
                DefaultClientConfigImpl clientConfig = DefaultClientConfigImpl.getClientConfigWithDefaultValues(client, namespace);
                ClientFactory.registerClientFromProperties(client, clientConfig);
                origins.put(client, new RibbonOrigin(client));
            }
        }
        catch (ClientException e) {
            throw new ZuulException(e, "Error initializing Ribbon clients.");
        }
    }

    @Override
    public Origin getOrigin(String name)
    {
        return origins.get(name);
    }
}
