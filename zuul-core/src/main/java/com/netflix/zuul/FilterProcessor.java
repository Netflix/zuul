/**
 * Copyright 2014 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.zuul;

import rx.Observable;

import java.util.List;

public class FilterProcessor {

    public Observable<EgressRequest> applyPreFilters(IngressRequest ingressReq, List<PreFilter> preFilters) {
        Observable<EgressRequest> observableReq = Observable.empty();
        if (preFilters != null && preFilters.size() != 0) {
            for (PreFilter filter: preFilters) {
                observableReq = observableReq.flatMap(egressReq -> filter.apply(ingressReq, egressReq));
            }
        }
        return observableReq;
    }

    //at the moment, this chains all n route filters and only returns the last
    //is there a use case for any sort of intelligent combining (amb?, flatMap?)
    public Observable<IngressResponse> applyRoutingFilters(EgressRequest egressReq, List<RouteFilter> routeFilters) {
        Observable<IngressResponse> observableResp = Observable.empty();
        if (routeFilters != null && routeFilters.size() != 0) {
            for (RouteFilter filter: routeFilters) {
                observableResp = observableResp.flatMap(ingressResp -> filter.apply(egressReq));
            }
        }
        return observableResp;
    }

    public Observable<EgressResponse> applyPostFilters(IngressResponse ingressResp, List<PostFilter> postFilters) {
        Observable<EgressResponse> observableResp = Observable.empty();
        if (postFilters != null && postFilters.size() != 0) {
            for (PostFilter filter: postFilters) {
                observableResp = observableResp.flatMap(egressResp -> filter.apply(ingressResp, egressResp));
            }
        }
        return observableResp;
    }
}
