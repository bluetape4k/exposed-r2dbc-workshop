# Spring Modulith custom R2DBC Publication Log

[English](README.md) | 한국어

이 예제는 Spring Modulith 형태의 order/fulfillment 경계를 보여주되, Spring
Modulith native event publication registry가 reactive라고 주장하지 않는다.

## Custom 구현인 이유

Spring Modulith의 `EventPublicationRepository`는 `List`, `Optional`, `void`를
반환하는 동기 SPI다. 공식 persistence 모듈도 JDBC, JPA, MongoDB, Neo4j이며
공식 R2DBC publication repository는 없다. 따라서 이 모듈은
`bluetape4k-exposed-spring-modulith`나 native event registry에 의존하지 않는다.

대신 order transaction이 Exposed R2DBC로 order와
`modulith_publication_log` row를 하나의 transaction에 기록한다. fulfillment
모듈의 coroutine dispatcher가 미완료 row를 읽어 handler를 호출하고
`COMPLETED` 또는 `FAILED`로 표시한다. 실패 row는 재처리하며 `attempts`를
증가시킨다.

## 모듈 경계

`orders.events`만 orders 모듈에서 공개하는 named interface다. fulfillment
모듈은 `allowedDependencies = ["orders :: events"]`만 선언한다.
`ApplicationModules.verify()`가 이 metadata를 검사하고 persistence 코드는
모두 `suspendTransaction`과 의도적인 `Flow` 수집을 사용한다.

## 실행

```bash
./gradlew :06-spring-modulith-publications:test -PuseDB=H2
```

테스트는 로컬 H2 R2DBC pool을 사용하며 JDBC `DataSource`, Docker, 외부 event
broker가 필요하지 않다.

## 검증하는 동작

- order와 publication log가 하나의 R2DBC transaction에 함께 commit되는지 확인;
- 미완료 publication을 fulfillment dispatcher가 완료하는지 확인;
- simulated downstream failure가 조회 가능하게 남고 재시도되는지 확인;
- Spring Modulith module metadata가 공개 event 경계를 검증하는지 확인.

native registry 계약은 [Spring Modulith events
reference](https://docs.spring.io/spring-modulith/reference/events.html)와
[EventPublicationRepository API](https://docs.spring.io/spring-modulith/docs/2.1.1/api/org/springframework/modulith/events/core/EventPublicationRepository.html)를
참고한다.
