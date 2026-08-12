# CBOR Serde Optimization Notes

Branch: `cbor-optimization`. Goal: optimize CBOR serialization/deserialization
performance in `runtime/serde/serde-cbor` without changing the public API and
keeping encoded output byte-for-byte identical for the same inputs (see the one
flagged exception below).

---

## Phase 1 — Architecture Summary

### Repo layout (relevant parts)

- `runtime/` — Kotlin Multiplatform runtime. Serde lives under `runtime/serde/`:
  - `serde/common` — shared serde primitives: `Serializer`, `Deserializer`,
    `SdkFieldDescriptor`, `SdkObjectDescriptor`, `FieldTrait`, the
    `PrimitiveDeserializer` interface, and the `ElementIterator`/`EntryIterator`/
    `FieldIterator` contracts.
  - `serde/serde-cbor` — the module under optimization.
  - `serde/serde-json`, `serde/serde-xml` — sibling formats (JSON read for insight).
  - `runtime-core` — `SdkBuffer`/`SdkBufferedSource`/`SdkBufferedSink` IO
    (a thin wrapper over **okio.Buffer** on JVM/Native).
- `codegen/` — Smithy→Kotlin code generators, including
  `rendering/serde/Cbor{Serializer,Parser,SerdeDescriptor}Generator.kt` and the
  `protocols/RpcV2Cbor.kt` protocol wiring. Generated serializers/deserializers
  drive the runtime `CborSerializer`/`CborDeserializer` through the generic
  `SerializeStructGenerator`/`DeserializeStructGenerator`.
- `tests/benchmarks/serde-benchmarks` — kotlinx-benchmark JMH harness. Had JSON
  and XML benchmarks but **no CBOR benchmark** (added in Phase 2).

### CBOR runtime data flow

**Serialize** (`CborSerializer.kt` + `encoding/`):
- `CborSerializer` holds a single `SdkBuffer`. Every `serializeX` / `field` /
  `entry` call constructs a short-lived `Value` subclass instance
  (`UInt`, `NegInt`, `Float32`, `Float64`, `TextString`, `ByteString`,
  `Boolean`, `Null`, `Tag`, `IndefiniteList`/`IndefiniteMap`, …) and calls
  `buffer.write(value)` → `value.encode(buffer)`.
- Structs and maps are encoded as **indefinite-length maps** (`0xBF … 0xFF`);
  lists as **indefinite-length lists** (`0x9F … 0xFF`). (There are TODOs about
  moving to definite-length once the interface can pass a count.)
- Head bytes + integer arguments are produced by `encodeArgument(major, arg)`
  in `CborUtils.kt`, which builds intermediate `ByteArray`s.
- Field names are looked up via `SdkFieldDescriptor.serialName`
  (`expectTrait<CborSerialName>().name`) on every field/entry.

**Deserialize** (`CborDeserializer.kt` + `encoding/`):
- `CborDeserializer(payload: ByteArray)` copies the payload into an `SdkBuffer`.
- Struct/map/list iterators read entries until an expected length is reached or
  an indefinite `break` (`0xFF`) is seen.
- Reading is driven by `peekMajor(buffer)` / `peekMinorByte(buffer)`
  (in `Major.kt` / `Minor.kt`), which each call **`buffer.peek()`** — on JVM this
  is `inner.peek().toSdk().buffer()`, allocating a fresh okio `PeekSource` +
  wrapper + buffered source **every call**. Values then re-read the same head
  byte to consume it.
- `decodeArgument` (Minor.kt) reads the minor bits, then for multi-byte
  arguments allocates a temporary `SdkBuffer` + `ByteArray` and folds it into a
  `ULong`.
- `Value.decode` is a recursive dispatcher used by `skipValue` and by nested
  containers (`List`, `Map`, bignum/decimal payloads, indefinite strings).

### Key primitives the encoders/decoders rely on

- `SdkBuffer` wraps `okio.Buffer`. Cheap: `readByte`, `writeByte`, `readShort/Int/Long`,
  `writeShort/Int/Long`, `readByteArray(n)`, `readUtf8(n)`, `writeUtf8`, `skip(n)`,
  `request(n)`, `exhausted()`.
- **Expensive**: `peek()` allocates (see above). `readFully(buffer, n)` into a
  temporary buffer + `readByteArray()` doubles copies.

### JSON implementation — techniques worth borrowing

`serde-json`'s `JsonLexer` operates **directly on the input `ByteArray` with an
integer cursor `idx`** — zero-allocation peeking (`data.getOrNull(idx)`), and
bulk `data.decodeToString(start, end)` for strings (comment notes it's ~3× faster
than char-by-char). This is the opposite of CBOR's per-byte `okio` peeking. The
main transferable ideas:
1. Avoid `peek()` allocations — read/inspect the head byte cheaply and reuse it.
2. Bulk-decode strings/byte arrays instead of copying through temp buffers.
3. Use fixed-width reads (`readShort/Int/Long`) instead of byte-array folds.
4. Avoid per-value intermediate object allocation on hot paths.
5. Cache field-name→index lookups instead of linear scans per field.

### Correctness constraints

- Public API is frozen (`runtime/serde/serde-cbor/api/serde-cbor.api`). No
  signature changes.
- Existing unit tests include exact hex vectors (e.g. bignum
  `c249010000000000000000`, decimal fraction `c48221196ab3`) — encoding must stay
  byte-identical.
- **FLAGGED behavior change:** `TextString.encode` currently writes
  `value.length` (UTF-16 unit count) as the CBOR string length but emits the
  UTF-8 bytes. For any non-ASCII/multibyte string these differ, producing
  **invalid, non-round-trippable CBOR** (verified: `"一二三"` encodes as length-3
  header `0x63` followed by 9 UTF-8 bytes; decode desyncs). The optimization
  fixes this to use the UTF-8 byte length. Output is **byte-identical for all
  ASCII inputs** (all existing tests) and becomes **spec-compliant** for
  multibyte. This is required for a correct round-trip benchmark on the
  CJK-heavy `twitter.json` dataset. See Phase 4.

---

## Phase 2 — Benchmark & baseline

Added a CBOR benchmark mirroring the JSON `TwitterBenchmark`, reusing `twitter.json`:

- New benchmark protocol `serdeCbor` in `tests/codegen/serde-codegen-support`
  (`SerdeCborProtocol` + `SerdeCborProtocolGenerator`, registered in
  `ProtocolSupplier`, the `TraitService`, and `protocols.smithy`). The `Twitter`
  service is annotated `@serdeCbor` in addition to `@serdeJson`.
- New `twitter-cbor` Smithy projection (package
  `aws.smithy.kotlin.benchmarks.serde.cbor.twitter`) wired into
  `build.gradle.kts` (+ `serde-cbor` dependency, + a `cbor` benchmark config).
- `CborTwitterBenchmark.kt` — `serializeBenchmark` and `deserializeBenchmark`
  over the generated CBOR (de)serializers. Because the generated CBOR serde is
  bound to its own model package/descriptors, the benchmark transcodes
  `twitter.json` → equivalent CBOR once at setup (generic `jsonToCbor`) and
  deserializes that into the CBOR-model `TwitterFeed`.

Prerequisite: the benchmark cannot round-trip the CJK-heavy dataset until the
`TextString.encode` UTF-8 length bug (S3 below) is fixed, so the perf **baseline
is measured against the S3-corrected encoder**. (The original encoder cannot
produce valid CBOR for this dataset at all.) Baseline numbers appear in the
Phase 4 summary table.

---

## Phase 3 — Ranked optimization opportunities

Ranked by expected impact on the twitter workload (string/int/map heavy).

### Deserialize path

**D1. Eliminate `peek()` allocations in `peekMajor`/`peekMinorByte`**
(`encoding/Major.kt:32`, `encoding/Minor.kt:37`). Each call does
`buffer.peek().readByte()`; on JVM `commonPeek()` = `inner.peek().toSdk().buffer()`
allocates a `PeekSource` + SDK wrapper + buffered source **per call**. Hot sites
peek 2–3× per value (`nextValueIsIndefiniteBreak`, `nextValueIsNull`,
`Value.decode`, `deserializeNumber`). Fix: peek the head byte **once** and derive
both major (`>> 5 & 7`) and minor (`& 0x1f`). **Highest allocation reduction.**

**D2. `decodeArgument` temp-buffer + fold** (`encoding/Minor.kt:41`). For
multi-byte arguments it allocates a temporary `SdkBuffer`, `readFully`s into it,
`readByteArray()`s, then folds to `ULong`. Replace with direct fixed-width reads
(`readByte`/`readShort`/`readInt`/`readLong`, big-endian). Runs for **every**
integer, string/byte length, and map/list length.

**D3. String/byte decode double-copy** (`encoding/Collections.kt`).
`TextString.decode`/`ByteString.decode` allocate a temp `SdkBuffer`, `readFully`,
`readByteArray`, then `decodeToString`. Replace with `buffer.readUtf8(len)` /
`buffer.readByteArray(len)`. Removes an extra `ByteArray` + copy per string.
**High** for string-heavy payloads.

**D4. Float decode byte-array fold** (`encoding/Numbers.kt`).
`readByteArray(4/8).toULong().toInt()/toLong()` → `readInt()` / `readLong()`.

**D5. `findNextFieldIndex` linear scan + per-field trait lookup**
(`CborDeserializer.kt:202-205`). O(fields) scan re-resolving `CborSerialName`
each comparison. Build a `Map<String, Int>` once. **Medium** (User has 38 fields).

### Serialize path

**S1. `encodeArgument` intermediate arrays + boxing** (`CborUtils.kt:32-58`).
`.map{}` (boxes each byte) + `.toByteArray()` + `byteArrayOf(head, *bytes)`
(spread copy) — 2 arrays + boxing per call, for every integer/length/tag id.
Replace with direct sink writes. **Highest serialize win.**

**S2. Float encode `.map{}.toByteArray()` boxing** (`encoding/Numbers.kt`).
Replace with `writeByte(head); writeInt(bits)` / `writeLong(bits)`.

**S3. `TextString.encode` UTF-16-length bug** (`encoding/Collections.kt`).
Wrote `value.length` (UTF-16 units) as a byte count → invalid CBOR for multibyte.
Fixed to UTF-8 byte length. Byte-identical for ASCII. **Correctness + perf.**
(FLAGGED behavior change; prerequisite for the benchmark.)

**S4. Per-primitive `Value` allocation in `CborSerializer`**
(`CborSerializer.kt`). Every `serializeInt/Long/…/Boolean` allocates a `Value`
object only to call `.encode`. With S1/S2, write primitives directly.

**S5. `beginMap`/`beginList` allocate a container object per call**
(`CborSerializer.kt:36,45`). Write the single head byte directly.

### Scope

All changes confined to `runtime/serde/serde-cbor` (+ benchmark harness). No
`runtime-core`/`SdkBuffer` or other-format changes. Public API unchanged.

---

## Phase 4 — Experiments (kept/reverted + numbers)

Each experiment: apply → run `serde-cbor` unit tests (must stay green) → re-run
CBOR benchmark → keep only if it measurably helps.

Environment: this Cloud Desktop (Linux, Corretto). Absolute ms differ from the
README's m5.4xlarge numbers; only relative before/after on this machine matter.
Benchmark config: 7 warmups + 5 measured iterations, avg time, ms/op.

**Baseline (S3-corrected encoder):**

| Benchmark                | Baseline (ms/op) |
|--------------------------|------------------|
| `deserializeBenchmark`   | 7.666 ± 0.046    |
| `serializeBenchmark`     | 0.681 ± 0.003    |

Deserialize dominates — the `peek()`-per-byte allocation pattern (D1) and the
temp-buffer decodes (D2/D3) are the prime suspects.

### Experiment log

**Exp 1 — D2+D3+D4: fixed-width reads in decode primitives.** `decodeArgument`
now uses `readByte/readShort/readInt/readLong`; `TextString.decode` uses
`readUtf8(len)`; `ByteString.decode` uses `readByteArray(len)`; float decode uses
`readInt/readLong`. Removes temp `SdkBuffer` + `ByteArray` + fold per
integer/length/string/float. Tests green.
- deserialize: 7.666 → **7.153** ms/op (−6.7%)
- serialize: 0.681 → 0.684 (noise)
- **KEPT.**

**Exp 2 — D1: peek the head byte once.** Added `peekHead`/`majorOf`/`minorOf`;
rewrote `peekMajor`/`peekMinorByte`, `nextValueIsIndefiniteBreak`,
`nextValueIsNull`, `Value.decode`, `Timestamp.decode` to peek the head byte a
single time and derive both major & minor from it (was 2–3 `peek()` allocations
per value). Also replaced `Major.fromValue` linear scan with a direct
`Major.entries[..]` index. Tests green.
- deserialize: 7.153 → **5.787** ms/op (−19%; −24.5% vs baseline)
- serialize: 0.684 → 0.668 (minor)
- **KEPT.** Confirms `peek()` allocation was the dominant deserialize cost.

**Exp 3 — D5: field-name → index map.** `CborFieldIterator` resolves each
field's serial name once into a `Map<String, Int>` instead of an O(fields) scan
with a trait-set lookup per comparison on every decoded field. Tests green.
- deserialize: 5.787 → **5.565** ms/op (−3.8%)
- serialize: unchanged (noise)
- **KEPT.**

**Exp 4 — S1+S2: direct-write encode arguments & floats.** Replaced
`encodeArgument` (which built a boxed `List<Byte>` + `ByteArray` + spread copy
per integer/length/tag id) with a `SdkBufferedSink.writeArgument` extension that
writes the head byte + fixed-width big-endian bytes directly. Float encode now
uses `writeInt`/`writeLong`. Removed the dead `encodeArgument` /
`ByteArray.toULong` helpers. Byte-identical output. Tests green.
- deserialize: 5.565 → 5.682 (noise)
- serialize: 0.681 (baseline) → **0.364** ms/op (−47%)
- **KEPT.** `encodeArgument` array+boxing was the dominant serialize cost.

**Exp 5 — S4+S5: skip per-primitive value allocations in `CborSerializer`.**
`serializeBoolean`/`serializeByte/Short/Int/Long`/`serializeString` and
`beginMap`/`beginList` now write directly to the buffer instead of allocating a
`Boolean`/`UInt`/`NegInt`/`TextString`/`IndefiniteMap`/`IndefiniteList` value.
NegInt semantics preserved exactly (`abs(value) - 1`). Byte-identical. Tests green.
- deserialize: 5.682 → 5.490 (noise)
- serialize: 0.364 → **0.351** ms/op (−3.6%)
- **KEPT.**

**Exp 6 — D6: skip per-primitive value allocations on decode.** Added
`decodeValue` helpers to `TextString`/`UInt`/`NegInt`/`Boolean` that return the
primitive directly; `deserializeString`/`deserializeBoolean`/`deserializeNumber`
and the struct field-name decode use them instead of allocating a wrapper value.
Tests green.
- deserialize: 5.490 → **5.429** ms/op (−1.1%, within error bars)
- serialize: unchanged
- **KEPT** — the wall-clock delta is within noise, but it removes an allocation
  per decoded number/string/bool/field-name (reduced GC pressure), is zero-risk,
  and mirrors the kept serialize-side change. No regression.

---

## Phase 4 (round 2) — eliminate remaining `peek()` allocations on decode

Environment: **macOS (Apple silicon, JDK/Corretto)** — a *different machine* from
the round-1 Linux Cloud Desktop, so absolute ms differ; only same-session
before/after deltas on this machine are meaningful. Benchmark: kotlinx-benchmark
JMH, 7 warmups + 5 measured iterations, avg time, ms/op, `jvmCborBenchmark`.

**Motivation.** Round-1 Exp 2 (D1) reduced the deserialize hot path from *2–3*
`peek()` calls per value to *one*, but a single `peek()` still remains on every
decoded value, and each `peek()` allocates a JVM `PeekSource` + SDK wrapper +
buffered source (documented as the dominant deserialize cost). A truly
zero-allocation lookahead would need a non-allocating indexed read on `SdkBuffer`,
but `SdkBuffer.inner` (the backing `okio.Buffer`) is `internal` to `runtime-core`
and out of the CBOR-only scope. **Key observation the round-1 pass missed:** on
several hot paths the peeked head byte is *always consumed anyway*, so `peek()`
(allocating) can be replaced with a direct `readByte()` (non-allocating) — no
non-consuming lookahead needed. This is a much smaller, safer change than the
full JSON-lexer-style cursor rewrite that round 1 (correctly) deferred.

**Baseline (this machine, HEAD before round-2), same-session:**

| Benchmark              | Cold-JVM run | Warm run (fair)  |
|------------------------|--------------|------------------|
| `deserializeBenchmark` | 3.199 ±0.502 | 2.546 ±0.136     |
| `serializeBenchmark`   | 0.207 ±0.011 | 0.201 ±0.006     |

(The first run was inflated by cold-JVM/JIT warmup — hence its ±0.5 error bar. The
warm run is the fair baseline for a same-session comparison.)

**Exp 7 — D7: read the head byte once instead of `peek()` where it is always
consumed.** Five decode-only edits (encoding untouched → output byte-identical by
construction; `git diff` shows no `encode`/serialize lines):
- `Minor.kt`: added `decodeArgument(buffer, head: UByte)` overload that decodes the
  argument from an already-read head byte; the old `decodeArgument(buffer)` now
  delegates to it after one `readByte()`.
- `CborDeserializer.deserializeNumber`: `readByte()` the head once → `majorOf(head)`
  → `decodeArgument(buffer, head)` (NEG_INT adds `+1u`), replacing `peekMajor` +
  `UInt/NegInt.decodeValue`. Removes a `peek()` per integer.
- `CborDeserializer.deserialize{Struct,Map,List}` + `deserializeExpectedLength`:
  `readByte()` the container head once (it is always consumed), check the major,
  and derive the length from the same byte — removing **both** `peek()` calls
  (`peekMajor` + `peekMinorByte`) per container.
- `Collections.kt` `TextString.decodeValue`: `readByte()` the head once; definite
  branch decodes length from it; the rare indefinite branch is inlined to mirror
  the old `IndefiniteList`-based path exactly (same `Value.decode(depth+1)` +
  `TextString` cast, same single break consume). Removes a `peek()` per field
  name / string value — the hottest decode path.
- `SimpleTypes.kt` `Boolean.decodeValue`: `readByte()` + `minorOf(...)` instead of
  `peekMinorByte` + `.also { readByte() }`. Removes a `peek()` per boolean.

All 249 serde-cbor JVM tests pass (the conformance suite has exact hex vectors for
indefinite strings `7fff`/`7f60ff`/`7f6063666f6fff`/…, all int widths, booleans,
and floats — these directly guard the decode changes). Two independent optimized
benchmark runs:

| Benchmark              | Warm baseline | Opt run 1     | Opt run 2     |
|------------------------|---------------|---------------|---------------|
| `deserializeBenchmark` | 2.546 ±0.136  | 2.270 ±0.206  | 2.256 ±0.165  |
| `serializeBenchmark`   | 0.201 ±0.006  | 0.197 ±0.007  | 0.217 ±0.014  |

- deserialize: 2.546 → ~2.26 ms/op = **−11%** (warm/warm; up to −29% vs the cold
  baseline). Reproducible across two runs.
- serialize: flat (~0.20) — a clean control, since no encode path was touched.
- **KEPT.** A real, reproducible deserialize win on top of the round-1 work, plus
  a `PeekSource` allocation removed on every decoded integer, boolean, field name,
  string, and container head (lower GC pressure). Zero behavior change for valid
  input (only difference: a malformed value now consumes its head byte before
  throwing — immaterial, as deserialization aborts on error).

### Ideas considered but NOT pursued

- **Definite-length maps/lists** (the existing TODOs). Would shrink payloads and
  speed decode, but changes the wire format (indefinite → definite) — **not
  byte-identical**, so out of scope here. Flagged as a follow-up.
- **Rewriting the deserializer onto a raw `ByteArray` cursor** (JSON-lexer style)
  to remove `peek()` entirely. Bigger win potential, but `CborPrimitiveDeserializer`
  and the `*.decode` companions are constructed with `SdkBufferedSource` and used
  that way by tests; a full cursor rewrite is high-risk for this pass. The
  single-peek change (D1) plus the peek→read change (Exp 7, D7) captured most of
  the benefit at low risk.
- **A non-allocating indexed peek** (e.g. `buffer[0]`) to remove the *remaining*
  `peek()` calls on the genuinely-speculative lookaheads (`nextValueIsNull`,
  `nextValueIsIndefiniteBreak`, `deserializeFloatingPoint`, `ByteString.decode`),
  where the head byte is *not* always consumed. This would need a non-allocating
  byte accessor on `SdkBuffer`, but its backing `okio.Buffer` is `internal` to
  `runtime-core` — a change outside the CBOR-only scope (and one that would touch
  the `runtime-core` public API contract). **Flagged as the highest-value
  follow-up** if the scope is ever widened to `runtime-core`: it would let every
  CBOR lookahead be allocation-free.
- **Caching `serialName` on the serialize side.** Would need changes to the
  shared `SdkFieldDescriptor` (out of scope: not CBOR-only).

---

## Summary

**Round 1** (Linux Cloud Desktop; Exp 1–6):

| Benchmark              | Baseline | Final | Change |
|------------------------|----------|-------|--------|
| `deserializeBenchmark` | 7.666    | 5.551 | −27.6% |
| `serializeBenchmark`   | 0.681    | 0.356 | −47.7% |

(ms/op, avg time; baseline = S3-corrected encoder; final = HEAD confirmation run.
Per-experiment deltas above; deserialize error bars ~±0.05–0.17 ms.)

**Round 2** (macOS; Exp 7 — `peek()`→`readByte()` where the head is always
consumed, on top of round 1):

| Benchmark              | Warm baseline | Final (~avg of 2 runs) | Change |
|------------------------|---------------|------------------------|--------|
| `deserializeBenchmark` | 2.546         | 2.26                   | −11%   |
| `serializeBenchmark`   | 0.201         | ~0.20 (flat)           | ~0%    |

(Same-session, same machine; two optimized runs 2.270 / 2.256. Round 1 and round 2
were measured on different machines, so the absolute numbers are not comparable
across rounds; each `Change` is a same-session before/after on its own machine.)

All 249 serde-cbor JVM tests pass. Encoded output is **byte-for-byte identical**
to the pre-optimization baseline (verified by serializing a comprehensive object
graph — all integer widths, negatives, `Long.MIN/MAX`, float/double, ASCII +
multibyte strings, null, instant, nested list/map, bignums, big decimals, byte
arrays — at both revisions and comparing SHA-256; identical). The public API
(`serde-cbor.api`) is unchanged.

### Verification performed

- `./gradlew :runtime:serde:serde-cbor:jvmTest` → BUILD SUCCESSFUL, 249 tests,
  0 failures.
- Byte-identity: probe hex at HEAD == probe hex at baseline commit `f52544c7`
  (SHA-256 match). CBOR round-trip of the CJK-heavy twitter payload succeeds.
- `./gradlew :runtime:serde:serde-cbor:jvmJar
  :tests:codegen:serde-codegen-support:build
  :tests:benchmarks:serde-benchmarks:compileKotlinJvm` → BUILD SUCCESSFUL.
- `serde-cbor.api` unchanged (`git diff` empty).

### Flagged behavior change

`TextString.encode` previously emitted the UTF-16 code-unit count as the CBOR
byte-length for text strings, producing **invalid, non-round-trippable CBOR for
any multibyte string** (verified). The fix uses the UTF-8 byte length. This is
**byte-identical for ASCII** (all existing tests / hex vectors unchanged) and is
a spec-compliance correctness fix for multibyte; it was also a prerequisite for
benchmarking the CJK-heavy twitter dataset. Callers relying on the previous
(broken) multibyte bytes — there should be none, as they never round-tripped —
would observe different output.
