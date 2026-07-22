# Changelog

## [1.1.12](https://github.com/virael/load-lab/compare/v1.1.11...v1.1.12) (2026-07-22)


### Bug Fixes

* **controller:** clear subIdStartedAt on cleanup to stop unbounded leak ([#84](https://github.com/virael/load-lab/issues/84)) ([ac264c8](https://github.com/virael/load-lab/commit/ac264c804ebc2157454347952a628729187d7cc8))


### Code Refactoring

* **controller:** extract shared finalize tail from onMetrics and… ([#85](https://github.com/virael/load-lab/issues/85)) ([713e3de](https://github.com/virael/load-lab/commit/713e3dedbe9308847055a4d1a69c9e7322e1cc5d))
* **web:** extract shared chart point-scaling logic ([#87](https://github.com/virael/load-lab/issues/87)) ([cb02bb9](https://github.com/virael/load-lab/commit/cb02bb911960facf68072742f1c9a57f6b797fef))

## [1.1.11](https://github.com/virael/load-lab/compare/v1.1.10...v1.1.11) (2026-07-22)


### Bug Fixes

* **controller:** reduce redispatched sub-run duration by elapsed time ([#82](https://github.com/virael/load-lab/issues/82)) ([0516a9e](https://github.com/virael/load-lab/commit/0516a9eea8e030ec6ec076ee0dbcd3a2e3fc64a8))

## [1.1.10](https://github.com/virael/load-lab/compare/v1.1.9...v1.1.10) (2026-07-22)


### Bug Fixes

* **controller:** ignore duplicate sub-run completions to prevent late… ([#80](https://github.com/virael/load-lab/issues/80)) ([bcb7f78](https://github.com/virael/load-lab/commit/bcb7f78f88c6c7c6a77f5d31f3b69bf6c21b66ad))

## [1.1.9](https://github.com/virael/load-lab/compare/v1.1.8...v1.1.9) (2026-07-22)


### Bug Fixes

* address architectural audit findings (KEDA ack-timing, Helm DB c… ([#78](https://github.com/virael/load-lab/issues/78)) ([8c8f34f](https://github.com/virael/load-lab/commit/8c8f34f7b6996034b60ddacb0db9e3f77a21e8c1))

## [1.1.8](https://github.com/virael/load-lab/compare/v1.1.7...v1.1.8) (2026-07-22)


### Documentation

* update README and docker-compose for worker capacity and access details ([41645bf](https://github.com/virael/load-lab/commit/41645bf0e07b1242e6af365a6540a4c1c534a7aa))

## [1.1.7](https://github.com/virael/load-lab/compare/v1.1.6...v1.1.7) (2026-07-22)


### Documentation

* add comprehensive project README (Apache 2.0, verified against running stack) ([#75](https://github.com/virael/load-lab/issues/75)) ([94e1685](https://github.com/virael/load-lab/commit/94e16854e4384a2ae9feb61da5c6d9a2c04e5431))

## [1.1.6](https://github.com/virael/load-lab/compare/v1.1.5...v1.1.6) (2026-07-22)


### Tests

* **sut:** cover config validation edge cases and latency range logic ([#73](https://github.com/virael/load-lab/issues/73)) ([06b1ce3](https://github.com/virael/load-lab/commit/06b1ce30bb2b01eedbb8aea15bb203d72e8a1c87))

## [1.1.5](https://github.com/virael/load-lab/compare/v1.1.4...v1.1.5) (2026-07-22)


### Tests

* **controller:** cover stuck sub-run watchdog, exception handler, la… ([#71](https://github.com/virael/load-lab/issues/71)) ([22d06da](https://github.com/virael/load-lab/commit/22d06da54ae7ea5475675b23b9f663ea92f0a1d6))

## [1.1.4](https://github.com/virael/load-lab/compare/v1.1.3...v1.1.4) (2026-07-22)


### Documentation

* **worker:** record final thread-per-VU vs reactive benchmark results ([#69](https://github.com/virael/load-lab/issues/69)) ([d037ba8](https://github.com/virael/load-lab/commit/d037ba89712bdfb6d94537036a0d1793eaaa85af))

## [1.1.3](https://github.com/virael/load-lab/compare/v1.1.2...v1.1.3) (2026-07-21)


### Continuous Integration

* **worker:** publish performance benchmark trend to GitHub Pages ([#62](https://github.com/virael/load-lab/issues/62)) ([50d8215](https://github.com/virael/load-lab/commit/50d8215a8ded02dfc2bd7a7bd3781ebbc46a5a65))

## [1.1.2](https://github.com/virael/load-lab/compare/v1.1.1...v1.1.2) (2026-07-21)


### Continuous Integration

* **worker:** add performance regression gate using real RunExecutorSer… ([#60](https://github.com/virael/load-lab/issues/60)) ([44dd30c](https://github.com/virael/load-lab/commit/44dd30c71f379cf2b445ab94c596bc8471d95978))

## [1.1.1](https://github.com/virael/load-lab/compare/v1.1.0...v1.1.1) (2026-07-21)


### Tests

* **worker:** add standalone benchmark harness comparing thread-per-V… ([#58](https://github.com/virael/load-lab/issues/58)) ([4daf0e6](https://github.com/virael/load-lab/commit/4daf0e698001b33319ea04957cfc1dd769dd417f))

## [1.1.0](https://github.com/virael/load-lab/compare/v1.0.1...v1.1.0) (2026-07-21)


### Features

* **worker:** replace thread-per-VU load engine with reactive WebClient ([3eb30b4](https://github.com/virael/load-lab/commit/3eb30b4f5ff15f8012e4fae17b416a337f27b6f6))

## [1.0.1](https://github.com/virael/load-lab/compare/v1.0.0...v1.0.1) (2026-07-20)


### Continuous Integration

* strengthen deploy smoke test into a real gate with automatic roll… ([#55](https://github.com/virael/load-lab/issues/55)) ([02eab64](https://github.com/virael/load-lab/commit/02eab64abc7696ae580800363add4e295f8fb706))

## 1.0.0 (2026-07-20)


### Features

* add aggregator service computing time-windowed metrics from mer… ([#42](https://github.com/virael/load-lab/issues/42)) ([185e666](https://github.com/virael/load-lab/commit/185e666063f93365c6a58da0a99bbfc447a68ff4))
* add docker-compose stack tying controller, worker, and sut toge… ([#26](https://github.com/virael/load-lab/issues/26)) ([7774580](https://github.com/virael/load-lab/commit/77745807c9214c51cf790f0393e5a76eadf5727b))
* add test history view and side-by-side run comparison ([#46](https://github.com/virael/load-lab/issues/46)) ([808c419](https://github.com/virael/load-lab/commit/808c4193ea7d5984e00237b06a25bb933020dccb))
* **aggregator:** add continuous aggregate rollup and retention policy ([#45](https://github.com/virael/load-lab/issues/45)) ([18aaef9](https://github.com/virael/load-lab/commit/18aaef9cac6b074194f8811931bd03b6db9e5cb7))
* **aggregator:** persist time-windowed metrics to TimescaleDB via Fl… ([#43](https://github.com/virael/load-lab/issues/43)) ([06c8609](https://github.com/virael/load-lab/commit/06c8609422746eb6faabcab8f149d0f528ded7fe))
* compute worker count from target load, add KEDA and CPU-HPA com… ([#50](https://github.com/virael/load-lab/issues/50)) ([c60f847](https://github.com/virael/load-lab/commit/c60f847de0c9d3867b0c88c7926f0a95184a927e))
* consolidate Kubernetes manifests into a templated Helm chart ([#51](https://github.com/virael/load-lab/issues/51)) ([3e3d546](https://github.com/virael/load-lab/commit/3e3d5468f5c81cd1bf21a20d909ad4fe9183c6ab))
* **controller:** add health check, retry with backoff, and abort sem… ([#23](https://github.com/virael/load-lab/issues/23)) ([1ad842d](https://github.com/virael/load-lab/commit/1ad842dde641580bbc9ccb2ded881096fd8e08e1))
* **controller:** add p50/p95/p99 latency percentiles via HdrHistogram ([#17](https://github.com/virael/load-lab/issues/17)) ([37a7140](https://github.com/virael/load-lab/commit/37a714075912546cc2d642107a8566de5ed0950f))
* **controller:** delegate load generation to worker over REST/SSE ([#22](https://github.com/virael/load-lab/issues/22)) ([470fbe2](https://github.com/virael/load-lab/commit/470fbe2d8ec136ae0364b7f9ed23b5155e4d49a2))
* **controller:** detect and redispatch stuck sub-runs, mark partial … ([#48](https://github.com/virael/load-lab/issues/48)) ([bdd00a9](https://github.com/virael/load-lab/commit/bdd00a907757c441722316be0c7c9f5d77b4440a))
* **controller:** introduce Kafka and publish test-run commands ([#27](https://github.com/virael/load-lab/issues/27)) ([9ca37b4](https://github.com/virael/load-lab/commit/9ca37b4e53ae05245a01f74b01fadd65acf4a55b))
* **controller:** isolate slow SSE clients via per-subscriber conflat… ([#49](https://github.com/virael/load-lab/issues/49)) ([1bb0fbe](https://github.com/virael/load-lab/commit/1bb0fbe642689a3597a308d214e595777f34fe07))
* **controller:** merge raw HdrHistogram data from workers for correc… ([#30](https://github.com/virael/load-lab/issues/30)) ([56531f9](https://github.com/virael/load-lab/commit/56531f9c6ed43f704462379f6bed7ded915eb104))
* **controller:** persist test definitions and outcomes to PostgreSQL ([#44](https://github.com/virael/load-lab/issues/44)) ([2ab76f2](https://github.com/virael/load-lab/commit/2ab76f24b1417f55617f1893b9c05bb4b976c53a))
* **controller:** stream live metrics via SSE ([#16](https://github.com/virael/load-lab/issues/16)) ([f4042c1](https://github.com/virael/load-lab/commit/f4042c115cde63c213ee7e4a4091b04afd1e4d13))
* split load across multiple workers with staggered ramp-up ([#29](https://github.com/virael/load-lab/issues/29)) ([374bfb6](https://github.com/virael/load-lab/commit/374bfb6dae351df51a6aba693560685a1e83a791))
* **sut:** add live config endpoint and latency range ([#4](https://github.com/virael/load-lab/issues/4)) ([64bd424](https://github.com/virael/load-lab/commit/64bd4249e65ced8f45e703186d52140db6d48a9b))
* **sut:** introduce SUT module with tunable delay and error rate ([#3](https://github.com/virael/load-lab/issues/3)) ([6c5321b](https://github.com/virael/load-lab/commit/6c5321b6d5c9b2ad1aeb01bb042b3c1d8ba88ee5))
* switch controller-worker communication to Kafka (commands + met… ([#28](https://github.com/virael/load-lab/issues/28)) ([ebe86ac](https://github.com/virael/load-lab/commit/ebe86acff1447edb3b1d243d8e7eeb2a6f7d3c18))
* **worker:** extract standalone load-generation service ([#21](https://github.com/virael/load-lab/issues/21)) ([e3f91fb](https://github.com/virael/load-lab/commit/e3f91fbae28777760246802a4cdcd666cfa315d0))


### Bug Fixes

* **ci:** repair jarmode extraction for Spring Boot 4.1 and align actuator across services ([#24](https://github.com/virael/load-lab/issues/24)) ([89f900a](https://github.com/virael/load-lab/commit/89f900aaf2564bdbd38f0bdb35d061652bed4131))
* **web:** make legend colors visible and explain percentile meaning ([#18](https://github.com/virael/load-lab/issues/18)) ([4afdaa6](https://github.com/virael/load-lab/commit/4afdaa6a3c3b9c9f44c1c6adc9e7d6bb890af024))


### Tests

* **aggregator:** add Testcontainers-based TimescaleDB integration test ([#47](https://github.com/virael/load-lab/issues/47)) ([44254e0](https://github.com/virael/load-lab/commit/44254e0b5a89c4e0659d3d6d8bf547b0f8dffc6b))
* **controller:** add integration test verifying live SSE streaming ([#19](https://github.com/virael/load-lab/issues/19)) ([83fb527](https://github.com/virael/load-lab/commit/83fb5277c6ed1e2d4f75ba6508f38384ae056896))
* **controller:** add Testcontainers-based end-to-end aggregation test ([#31](https://github.com/virael/load-lab/issues/31)) ([fe0ca21](https://github.com/virael/load-lab/commit/fe0ca21beeb64cf714bdd22425146117d8db55c1))


### Continuous Integration

* add cached docker-compose integration job for multi-worker smoke … ([#32](https://github.com/virael/load-lab/issues/32)) ([3d7056f](https://github.com/virael/load-lab/commit/3d7056f8f31e3d86568cb4d3674d7933942c6e22))
* add Spotless and ESLint quality gates ([#6](https://github.com/virael/load-lab/issues/6)) ([1655180](https://github.com/virael/load-lab/commit/16551801cb9e7ae1dcb75f16c8889b57ccc471f2))
* automate changelog and versioned releases with release-please ([#52](https://github.com/virael/load-lab/issues/52)) ([41b515a](https://github.com/virael/load-lab/commit/41b515af8098c781cdaaed2a9b8f7e32bff818e8))
* automate changelog and versioned releases with release-please ([#53](https://github.com/virael/load-lab/issues/53)) ([4600709](https://github.com/virael/load-lab/commit/4600709a64d8eeee142bda39dfd4629bc53dab00))
* configure Dependabot for Maven, npm, and GitHub Actions ([#7](https://github.com/virael/load-lab/issues/7)) ([6e28eff](https://github.com/virael/load-lab/commit/6e28effa0f7cefadd67bbdf4ac28913f9507f9b4))
* **controller:** add JaCoCo coverage report and PR comment ([#20](https://github.com/virael/load-lab/issues/20)) ([1762e70](https://github.com/virael/load-lab/commit/1762e70a3b42537d116f85051019ddd135068847))
* enforce conventional commits with commitlint + husky ([ec7bd4e](https://github.com/virael/load-lab/commit/ec7bd4e02c02ee3fa48f15245b99884a2ad1c7bd))
* publish Docker images to GHCR on merge to main ([#25](https://github.com/virael/load-lab/issues/25)) ([a88f26a](https://github.com/virael/load-lab/commit/a88f26abda8787e3a04b2b4d04f4fc8da5139a96))
* sync ci and commitlint workflows across services ([#5](https://github.com/virael/load-lab/issues/5)) ([90ec260](https://github.com/virael/load-lab/commit/90ec260ceb62c2f0612588f0e2008843af2a0efc))
