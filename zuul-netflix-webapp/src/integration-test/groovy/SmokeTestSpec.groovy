
import groovyx.net.http.RESTClient
import spock.lang.Specification

/**
 * User: michaels@netflix.com
 * Date: 5/22/15
 * Time: 5:23 PM
 */
class SmokeTestSpec extends Specification
{
    static int port = Integer.parseInt(System.getProperty("integrationtest.port", "8080"))
    def client

    def setup() {
        client = new RESTClient( 'http://localhost:' + port )
    }

    def cleanup() {
        client.shutdown()
    }

    def "Healthcheck"()
    {
        when:
        def resp = client.get([ path: '/healthcheck' ])
        then:
        with(resp) {
            status == 200
            contentType == "application/xml"
        }
        with(resp) {
            resp.responseData.name() == "health"
            resp.responseData.text() == "ok"
        }
    }

    def "Proxy to API service"()
    {
        when:
        def resp = client.get([ path: '/account/geo' ])
        then:
        with(resp) {
            status == 200
            contentType == "text/plain"
            resp.responseData.str.contains("country")
        }
    }
}