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
    static NettySampleStartServer server
    static int port = 9019

    def setupSpec()
    {
        // Startup the rxnetty server.
        System.setProperty("archaius.deployment.stack", "integrationtest")
        server = new NettySampleStartServer()
        server.init(port, false)
    }

    def cleanupSpec()
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

    def "Proxy to API service"()
    {
        setup:
        def client = new RESTClient( 'http://localhost:' + port )
        when:
        def resp = client.get([ path: '/account/geo' ])
        then:
        with(resp) {
            status == 200
            contentType == "text/plain"
            headers.server && headers.server.contains("api ")
        }
    }
}
