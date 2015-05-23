
import groovyx.net.http.RESTClient
import spock.lang.Specification

/**
 * User: michaels@netflix.com
 * Date: 5/22/15
 * Time: 5:23 PM
 */
class SmokeTestSpec extends Specification {
    def "Should return 200"() {
        setup:
        def primerEndpoint = new RESTClient( 'http://localhost:8099' )
        when:
        def resp = primerEndpoint.get([ path: '/healthcheck' ])
        then:
        with(resp) {
            status == 200
            contentType == "application/xml"
        }
//        with(resp.data) {
//            payload == "<health>ok</health>"
//        }
    }
}