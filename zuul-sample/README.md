# zuul-sample

`zuul-sample` is a local reference application, not a production TLS configuration.

Each build generates short-lived self-signed server and client certificates into `build/generated/resources/dev-ssl/ssl/`, and the packaged runtime resources land under `build/resources/main/ssl/`. Do not copy these generated materials into real deployments. Production environments must provision their own independently generated certificates, private keys, truststores, and rotation process.

If you want to exercise the sample mutual TLS path after running `:zuul-sample:processResources` or `:zuul-sample:run`, use the generated client materials under `zuul-sample/build/resources/main/ssl/`.
