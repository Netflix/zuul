package preProcess

import com.netflix.zuul.groovy.ZuulFilter


class filter extends ZuulFilter {

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
        println("First filter test!!")
        return null
    }


}


