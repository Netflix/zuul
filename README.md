# Zuul

Zuul is an edge service that provides dynamic routing, monitoring, resiliency, security, and more.
Please view the wiki for usage, information, HOWTO, etc https://github.com/Netflix/zuul/wiki

Here are some links to help you learn more about the Zuul Project. Feel free to PR to add any other info, presentations, etc.

---

## Zuul 2
  A lot of people are asking about the status of Zuul 2.0.  We are actively working on open sourcing it and with it, likely many filters that we use at Netflix. Yes, we realize it's been a long time coming. When we initially wrote Zuul 2.0, we heavily relied on RxJava to string filters together with Netty. This ended up adding a lot of complexity to the Zuul 2.0 core as well as made it quite difficult to operate and debug. We didn't think it was right to release Zuul 2.0 like this. So we spent a lot of time refactoring out this pattern, using Netty constructs directly. This took the better part of a year to complete and deploy safely within Netflix. So this work is now done. We are working towards releasing this much better, easier to understand, and more reliable Zuul 2.0.  Obviously Netflix's business priorities take precedence to our open sourcing efforts, so as we get free time we will put efforts to open sourcing! Stay Tuned.
  
Current Zuul 2 development is on the __2.1__ branch

---

Articles from Netflix:

Zuul 1: http://techblog.netflix.com/2013/06/announcing-zuul-edge-service-in-cloud.html

Zuul 2: http://techblog.netflix.com/2016/09/zuul-2-netflix-journey-to-asynchronous.html

---

Netflix presentations about Zuul:

Strange Loop 2017 - Zuul 2: https://youtu.be/2oXqbLhMS_A

---

Slides from Netflix presentations about Zuul:

http://www.slideshare.net/MikeyCohen1/zuul-netflix-springone-platform

http://www.slideshare.net/MikeyCohen1/rethinking-cloud-proxies-54923218

https://github.com/strangeloop/StrangeLoop2017/blob/master/slides/ArthurGonigberg-ZuulsJourneyToNonBlocking.pdf

---

Projects Using Zuul:

https://cloud.spring.io/

https://jhipster.github.io/

---

Info and examples from various projects:

https://spring.io/guides/gs/routing-and-filtering/

http://www.baeldung.com/spring-rest-with-zuul-proxy

https://blog.heroku.com/using_netflix_zuul_to_proxy_your_microservices

http://kubecloud.io/apigatewaypattern/

http://blog.ippon.tech/jhipster-3-0-introducing-microservices/

---

Other blog posts about Zuul:

https://engineering.riotgames.com/news/riot-games-api-fulfilling-zuuls-destiny

https://engineering.riotgames.com/news/riot-games-api-deep-dive

http://instea.sk/2015/04/netflix-zuul-vs-nginx-performance/

---
