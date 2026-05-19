> 한국어 버전: [README.ko.md](README.ko.md)

# 03 SQL Functions

An example module for **SQL Functions** available in the Exposed R2DBC DSL. Covers SQL built-in and custom functions — string/bitwise operations, math functions, statistics functions, trigonometric functions, and window functions — across 6 test files.

## Learning Objectives

- Understand how to use SQL functions in the Exposed DSL
- Use string, math, statistics, and trigonometric functions
- Write analytical queries with window functions
- Learn how to define and use custom functions

## Tech Stack

| Category  | Technology                                                  |
|-----------|-------------------------------------------------------------|
| ORM       | Exposed R2DBC DSL                                           |
| Async     | Kotlin Coroutines                                           |
| DB        | H2 (default), MariaDB, MySQL 8, PostgreSQL                  |
| Container | Testcontainers                                              |
| Testing   | JUnit 5 + bluetape4k-assertions + ParameterizedTest (multi-DB support)     |

## Function Categories

![Function Categories 1](../../docs/images/readme-diagrams/05-exposed-r2dbc-dml-03-functions-diagram-01.png)

## Function Class Hierarchy (Class Diagram)

![Function Class Hierarchy (Class Diagram) 2](../../docs/images/readme-diagrams/05-exposed-r2dbc-dml-03-functions-diagram-02.png)

## Project Structure

```
src/test/kotlin/exposed/r2dbc/examples/functions/
├── Ex00_FunctionBase.kt           # Common base class: helper to evaluate functions against the DUAL table
├── Ex01_Functions.kt              # General functions: string (upper, lower, concat, charLength), bitwise, case/when, coalesce, CustomFunction
├── Ex02_MathFunction.kt           # Math functions: abs, ceil, floor, round, sqrt, exp, power, sign
├── Ex03_StatisticsFunction.kt     # Statistics functions: stdDevPop, stdDevSamp, varPop, varSamp
├── Ex04_TrigonometricalFunction.kt # Trigonometric functions: sin, cos, tan, asin, acos, atan, cot, degrees, radians, pi
└── Ex05_WindowFunction.kt         # Window functions: rowNumber, rank, denseRank, lead, lag, firstValue, lastValue, nthValue, ntile, cumeDist, percentRank
```

> **Note**: This module has no `src/main`. All code lives in `src/test` — it is a test-only learning module.

## Example Details

### Ex00_FunctionBase - Common Base Class

The parent class for all function tests. Provides a `shouldExpressionEqualTo` helper that evaluates SQL function results using the `DUAL` table.

```kotlin
// Evaluate function result against the DUAL table
protected suspend infix fun <T> SqlFunction<T>.shouldExpressionEqualTo(expected: T) {
    val result = Table.Dual.select(this).first()[this]
    result shouldBeEqualTo expected
}
```

### Ex01_Functions - General Functions

| Category    | Functions                                                                                        |
|-------------|--------------------------------------------------------------------------------------------------|
| String      | `UpperCase`, `LowerCase`, `Concat`, `CharLength`, `substring`, `trim`, `locate`                  |
| Bitwise     | `bitwiseAnd`, `bitwiseOr`, `bitwiseXor`                                                          |
| Conditional | `case`/`when`, `Coalesce`, `andIfNotNull`, `orIfNotNull`                                         |
| Arithmetic  | `DivideOp`, `ModOp`, `Sum`                                                                       |
| Custom      | `CustomStringFunction`, `CustomLongFunction`, `CustomFunction`, `CustomOperator`                  |

### Ex02_MathFunction - Math Functions

| Function          | Description                      |
|-------------------|----------------------------------|
| `AbsFunction`     | Absolute value                   |
| `CeilingFunction` | Ceiling (round up)               |
| `FloorFunction`   | Floor (round down)               |
| `RoundFunction`   | Round to specified decimal places |
| `SqrtFunction`    | Square root                      |
| `ExpFunction`     | Exponential function (e^x)       |
| `PowerFunction`   | Exponentiation                   |
| `SignFunction`    | Sign (-1, 0, 1)                  |

### Ex03_StatisticsFunction - Statistics Functions

| Function      | Description                |
|---------------|----------------------------|
| `stdDevPop`   | Population standard deviation |
| `stdDevSamp`  | Sample standard deviation  |
| `varPop`      | Population variance        |
| `varSamp`     | Sample variance            |

### Ex04_TrigonometricalFunction - Trigonometric Functions

Covers the Exposed DSL mapping for SQL trigonometric functions: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `cot`, `degrees`, `radians`, `pi`.

### Ex05_WindowFunction - Window Functions

Comprehensive examples of SQL analytical (window) functions:

| Category     | Functions                                                                                           |
|--------------|-----------------------------------------------------------------------------------------------------|
| Ranking      | `rowNumber`, `rank`, `denseRank`, `percentRank`, `cumeDist`, `ntile`                               |
| Value access | `lead`, `lag`, `firstValue`, `lastValue`, `nthValue`                                                |
| Aggregate    | `sum`, `avg`, `count`, `min`, `max`, `stdDevPop`, `stdDevSamp`, `varPop`, `varSamp` (with OVER)     |
| Frame        | `WindowFrameBound`, `ROWS`, `RANGE`, `UNBOUNDED PRECEDING/FOLLOWING`                               |

```kotlin
// Window function usage example
val rowNum = rowNumber().over()
    .partitionBy(sales.product)
    .orderBy(sales.amount, SortOrder.DESC)

sales.select(sales.product, sales.amount, rowNum)
    .toList()
```

## Function Reference Tables

### Math Functions

| Function        | SQL Mapping      | Description                 | Supported DBs        |
|-----------------|------------------|-----------------------------|----------------------|
| `abs(col)`      | ABS(col)         | Absolute value              | All                  |
| `floor(col)`    | FLOOR(col)       | Floor                       | All                  |
| `ceiling(col)`  | CEILING(col)     | Ceiling                     | All                  |
| `round(col, n)` | ROUND(col, n)    | Round                       | All                  |
| `sqrt(col)`     | SQRT(col)        | Square root                 | All                  |
| `power(col, n)` | POWER(col, n)    | Exponentiation              | All                  |
| `exp(col)`      | EXP(col)         | Natural exponent            | All                  |
| `ln(col)`       | LN(col)          | Natural logarithm           | All                  |
| `log(base, col)`| LOG(base, col)   | Logarithm                   | PostgreSQL, MySQL    |
| `mod(a, b)`     | MOD(a, b)        | Remainder                   | All                  |
| `sign(col)`     | SIGN(col)        | Sign (-1, 0, 1)             | All                  |

### Aggregate Functions

| Function          | SQL Mapping          | Description                |
|-------------------|----------------------|----------------------------|
| `count(col)`      | COUNT(col)           | Row count                  |
| `sum(col)`        | SUM(col)             | Sum                        |
| `avg(col)`        | AVG(col)             | Average                    |
| `min(col)`        | MIN(col)             | Minimum                    |
| `max(col)`        | MAX(col)             | Maximum                    |
| `stdDevPop(col)`  | STDDEV_POP(col)      | Population standard deviation |
| `stdDevSamp(col)` | STDDEV_SAMP(col)     | Sample standard deviation  |
| `varPop(col)`     | VAR_POP(col)         | Population variance        |
| `varSamp(col)`    | VAR_SAMP(col)        | Sample variance            |

### Window Functions

| Function        | SQL Mapping       | Description                          |
|-----------------|-------------------|--------------------------------------|
| `rowNumber()`   | ROW_NUMBER()      | Row number within partition          |
| `rank()`        | RANK()            | Rank with gaps for ties              |
| `denseRank()`   | DENSE_RANK()      | Rank without gaps for ties           |
| `lead(col, n)`  | LEAD(col, n)      | Value n rows ahead                   |
| `lag(col, n)`   | LAG(col, n)       | Value n rows behind                  |
| `firstValue()`  | FIRST_VALUE()     | First value in partition             |
| `lastValue()`   | LAST_VALUE()      | Last value in partition              |
| `ntile(n)`      | NTILE(n)          | Classify into n buckets              |
| `percentRank()` | PERCENT_RANK()    | Percentile rank (0.0–1.0)            |
| `cumeDist()`    | CUME_DIST()       | Cumulative distribution (0.0–1.0)    |

### Trigonometric Functions

| Function        | SQL Mapping    | Description             |
|-----------------|----------------|-------------------------|
| `sin(col)`      | SIN(col)       | Sine                    |
| `cos(col)`      | COS(col)       | Cosine                  |
| `tan(col)`      | TAN(col)       | Tangent                 |
| `asin(col)`     | ASIN(col)      | Arcsine                 |
| `acos(col)`     | ACOS(col)      | Arccosine               |
| `atan(col)`     | ATAN(col)      | Arctangent              |
| `degrees(col)`  | DEGREES(col)   | Radians to degrees      |
| `radians(col)`  | RADIANS(col)   | Degrees to radians      |
| `pi()`          | PI()           | Pi constant             |

## Shared Test Infrastructure

- `Ex00_FunctionBase` — Base class for function evaluation
- `DMLTestData.Sales` — Sales data used in window function tests
- `R2dbcExposedTestBase` — Multi-DB test support

## Running Tests

```bash
# Run all Functions tests
./gradlew :03-functions:test

# Run a specific test class
./gradlew :03-functions:test --tests "exposed.r2dbc.examples.functions.Ex05_WindowFunction"
```

## Further Reading

- [7.3 Functions](https://debop.notion.site/1ca2744526b0805e9689efa4a03d01df?v=1ca2744526b08138857a000c9847c052)
