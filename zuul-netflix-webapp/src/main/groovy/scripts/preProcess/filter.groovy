import com.netflix.zuul.groovy.ProxyFilter


class filter extends ProxyFilter {

    @Override
    String filterType() {
        return 'pre'
    }

    @Override
    int filterOrder() {
        return 1
    }

    boolean shouldFilter() {
        return true
    }

    Object run() {
println("filter test!!")
        return null
    }


}


