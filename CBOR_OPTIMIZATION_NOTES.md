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
