import com.netflix.zuul.NettySampleStartServer
import groovyx.net.http.RESTClient
import spock.lang.Specification

/**
 * User: michaels@netflix.com
 * Date: 5/22/15
 * Time: 5:23 PM
 */
class SmokeTestSpec extends Specification
{
    NettySampleStartServer server
    int port = 9019

    def setup()
    {
        String env = "test"
        String region = "us-east-1"
        String stack = "local"
        System.setProperty("archaius.deployment.environment", env)
        System.setProperty("archaius.deployment.region", region)
        System.setProperty("archaius.deployment.stack", stack)
        System.setProperty("eureka.environment", env)
        System.setProperty("eureka.region", region)
        System.setProperty("eureka.stack", stack)

        // Startup the rxnetty server.
        server = new NettySampleStartServer()
        server.init(port, false)
    }

    def cleanup()
    {
        // Shutdown rxnetty server.
        server.shutdown()
    }

    def "Healthcheck"()
    {
        setup:
        def client = new RESTClient( 'http://localhost:' + port )
        when:
        def resp = client.get([ path: '/healthcheck' ])
        then:
        with(resp) {
            status == 200
            contentType == "text/plain"
        }
        with(resp.data) {
            str == "OK"
        }
    }
}
