# 06 Advanced (고급 기능)

이 디렉토리는 Exposed R2DBC의 고급 기능을 다루는 모듈들을 포함합니다. 암호화, 날짜/시간, JSON, 커스텀 컬럼 타입, 다양한 JSON 라이브러리 통합 등 실무에서 자주 필요한 고급 패턴을 학습합니다.

## 모듈 목록

### [01 Exposed R2DBC Crypt (투명한 컬럼 암호화)](01-exposed-r2dbc-crypt/README.md)

`exposed-crypt` 확장을 사용하여 R2DBC 환경에서 데이터베이스 컬럼을 투명하게 암호화/복호화합니다.

| 특징          | 설명                                                         |
|-------------|------------------------------------------------------------|
| 지원 알고리즘     | `AES_256_PBE_CBC`, `AES_256_PBE_GCM`, `BLOW_FISH`, `TRIPLE_DES` |
| DSL/DAO 지원  | 두 스타일 모두 지원                                                |
| WHERE 검색    | 비결정적 암호화로 **불가** → 검색 필요 시 `10-exposed-r2dbc-jasypt` 참조    |

---

### [02 Exposed R2DBC JavaTime (java.time 통합)](02-exposed-r2dbc-javatime/README.md)

Java 8의 `java.time` (JSR-310) API와 Exposed R2DBC의 통합 방법을 학습합니다.

| 컬럼 타입                          | Java 타입                    |
|--------------------------------|----------------------------|
| `date(name)`                   | `java.time.LocalDate`      |
| `time(name)`                   | `java.time.LocalTime`      |
| `datetime(name)`               | `java.time.LocalDateTime`  |
| `timestamp(name)`              | `java.time.Instant`        |
| `timestampWithTimeZone(name)`  | `java.time.OffsetDateTime` |
| `duration(name)`               | `java.time.Duration`       |

---

### [03 Exposed R2DBC Kotlinx-Datetime](03-exposed-r2dbc-kotlin-datetime/README.md)

`kotlinx.datetime` 라이브러리와 Exposed R2DBC의 통합 방법을 학습합니다. Kotlin Multiplatform 프로젝트에 적합합니다.

---

### [04 Exposed R2DBC JSON (JSON/JSONB 지원)](04-exposed-r2dbc-json/README.md)

`exposed-json` 모듈(`kotlinx.serialization` 기반)을 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 다룹니다.

| 기능                    | 설명               |
|-----------------------|------------------|
| `.extract<T>(path)`   | JSON 필드 추출       |
| `.contains(value)`    | JSON 포함 여부 확인    |
| `.exists(path)`       | JSONPath 존재 여부 확인 |

---

### [05 Exposed R2DBC Money (금융 데이터 처리)](05-exposed-r2dbc-money/README.md)

`exposed-money` 모듈을 사용하여 JavaMoney(`javax.money`) API로 통화 값을 안전하게 처리합니다. `compositeMoney`가 금액(`DECIMAL`)과 통화(`VARCHAR`) 컬럼을 단일 속성으로 관리합니다.

---

### [06 커스텀 컬럼 타입](06-exposed-r2dbc-custom-columns/README.md)

사용자 정의 컬럼 타입을 구현하는 방법을 학습합니다.

| 기능             | 설명                                    |
|----------------|---------------------------------------|
| **커스텀 ID 생성기** | Snowflake, KSUID, TimebasedUUID 자동 생성 |
| **투명한 압축**     | LZ4, Snappy, Zstd를 이용한 자동 압축/해제       |
| **결정적 암호화**    | AES 등 결정적 암호화로 `WHERE` 절 검색 가능        |
| **바이너리 직렬화**   | Kryo/Fory 기반 객체 직렬화 + 압축 조합           |

---

### [07 커스텀 Entity (ID 생성 전략)](07-exposed-r2dbc-custom-entities/README.md)

Snowflake, KSUID, Time-based UUID 등 다양한 ID 생성 전략을 캡슐화한 커스텀 기반 테이블/엔티티 클래스를 구현합니다.

| 기반 클래스                     | ID 타입            | 생성 전략               |
|----------------------------|------------------|---------------------|
| `SnowflakeIdTable`         | `Long`           | Snowflake 알고리즘      |
| `KsuidTable`               | `String (27자)`   | KSUID               |
| `KsuidMillisTable`         | `String (27자)`   | 밀리초 정밀도 KSUID       |
| `TimebasedUUIDTable`       | `java.util.UUID` | 시간 기반(버전 1) UUID    |
| `TimebasedUUIDBase62Table` | `String (22자)`   | 시간 기반 UUID + Base62 |

---

### [08 Exposed R2DBC Jackson (Jackson 기반 JSON)](08-exposed-r2dbc-jackson/README.md)

Jackson 2.x 라이브러리를 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 처리합니다. `@Serializable` 어노테이션 없이 표준 Kotlin 데이터 클래스를 사용할 수 있습니다.

---

### [09 Exposed R2DBC Fastjson2](09-exposed-r2dbc-fastjson2/README.md)

Alibaba Fastjson2 라이브러리를 사용하여 JSON 컬럼을 처리합니다. 뛰어난 JSON 직렬화 성능이 필요한 애플리케이션에 적합합니다.

---

### [10 Exposed R2DBC Jasypt (결정적 암호화)](10-exposed-r2dbc-jasypt/README.md)

Jasypt를 사용하여 R2DBC 환경에서 **결정적(검색 가능한)** 암호화를 구현합니다. 동일 평문이 항상 동일 암호문을 생성하므로 `WHERE` 절에서 직접 쿼리할 수 있습니다.

| 컬럼 타입                                       | 설명                      |
|---------------------------------------------|-------------------------|
| `jasyptVarChar(name, length, encryptor)`    | 검색 가능한 암호화 문자열          |
| `jasyptBinary(name, length, encryptor)`     | 검색 가능한 암호화 바이너리         |

---

### [11 Exposed R2DBC Jackson 3](11-exposed-r2dbc-jackson3/README.md)

Jackson 3.x 버전을 사용하여 R2DBC 환경에서 JSON/JSONB 컬럼을 처리합니다.

---

### [12 Exposed R2DBC Tink (Google Tink 기반 암호화)](12-exposed-r2dbc-tink/README.md)

**Google Tink** 암호화 라이브러리를 Exposed R2DBC와 통합합니다. DAEAD(결정적, 검색 가능)와 AEAD(비결정적, 고보안) 두 가지 암호화 방식을 선택적으로 사용할 수 있습니다.

| 컬럼 타입                                               | 암호화 방식  | WHERE 검색 |
|-----------------------------------------------------|---------|----------|
| `tinkDaeadVarChar(name, length)`                    | DAEAD   | 가능       |
| `tinkAeadVarChar(name, length, algorithm)`          | AEAD    | 불가       |
| `tinkAeadBinary(name, length, algorithm)`           | AEAD    | 불가       |

---

## 암호화 모듈 비교

| 모듈                              | 암호화 방식         | WHERE 검색          | 라이브러리       |
|---------------------------------|----------------|-------------------|-------------|
| `01-exposed-r2dbc-crypt`        | 비결정적           | 불가                | Bouncy Castle |
| `10-exposed-r2dbc-jasypt`       | 결정적            | 가능                | Jasypt      |
| `12-exposed-r2dbc-tink`         | 결정적/비결정적 선택 가능 | DAEAD만 가능         | Google Tink |
| `06-exposed-r2dbc-custom-columns` | 커스텀 구현       | 구현에 따라 다름         | AES 직접 구현  |

## 테스트 실행

```bash
# 고급 기능 모듈 전체 테스트
./gradlew :01-exposed-r2dbc-crypt:test
./gradlew :12-exposed-r2dbc-tink:test

# H2만 사용하는 빠른 테스트
./gradlew :12-exposed-r2dbc-tink:test -PuseFastDB=true
```
