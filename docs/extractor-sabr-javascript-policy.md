# SABR JavaScript policy contract

A policy payload contains signed JavaScript source. It must define one global function:

```js
function createSabrPolicy(sabr) {
  return {
    describe: function () {},
    initialRequest: function (event) {},
    followUpRequest: function (event) {},
    response: function (event) {},
    demandRoute: function (event) {},
    demandResponse: function (event) {},
    mediaHeader: function (event) {}
  };
}
```

Each playback or download session gets a new policy object. Properties stored on that object are
session state and survive calls. PipePipe Client uses QuickJS with no Java object bindings and with
bounded native memory, stack, input, and output sizes. The current binding does not expose
QuickJS's interrupt handler, so execution time is not bounded by the runtime; signed policies must
be tested for termination before release.

`sabr.base64.decode(string)` returns an unsigned byte array and `sabr.base64.encode(bytes)` returns a
string. `sabr.proto.decode(bytes)` returns protobuf fields shaped as `{n, w, v}` for varints or
`{n, w, b}` for byte/fixed fields. `sabr.proto.encode(fields)` performs the inverse operation. A
policy can include any additional ordinary JavaScript helpers it needs.

`describe()` returns media envelope part types:

```js
{demand: true,
 media: {headerType: 20, mediaType: 21, endType: 22, headerDecoder: "builtin"}}
```

`headerDecoder: "builtin"` keeps the Host's existing protobuf media-header decoder when that
schema has not changed, avoiding JavaScript on the per-segment hot path. Omit it when the policy
needs `mediaHeader(event)` to implement a changed header schema.

Set `demand: true` only when the policy implements both demand methods below. If it is absent or
false, the Host uses the bundled demand behavior, preserving compatibility with older signed
policies.

Request methods receive `requestNumber`, recovery counters, player/buffer metrics, and
`fallbackBody`, the Base64-encoded builtin request. They return `{body: "..."}`. The script may use,
patch, or completely replace the builtin protobuf.

`response(event)` receives the recovery state, mode, media segment count, a bounded array of raw
non-media UMP parts (`{type, data}`), and a `builtin` diagnostic interpretation. It returns an
ordered `actions` array and optional `backoffMs`, `redirectUrl`, `errorDetails`, and recovery
`state`. Actions are validated by the Host before execution; the only available capabilities are
the values in `SabrSessionPolicy.ActionType`.

To replace response schemas, return `APPLY_RESPONSE_STATE` and a normalized `statePatch` object.
The patch may contain `nextRequest`, `live`, `formats`, `contexts`, and `contextPolicy`. This lets a
policy update playback cookies and pacing, live heads, format initialization metadata, and SABR
contexts without constructing Java protocol objects. Array counts and byte values are bounded by
the Host. `APPLY_BUILTIN_RESPONSE_STATE` remains available for policies that only replace control
decisions and still use the bundled Java response decoder.

```js
{
  actions: ['APPLY_RESPONSE_STATE', 'CONTINUE'],
  statePatch: {
    nextRequest: {
      targetAudioReadaheadMs: 15000,
      targetVideoReadaheadMs: 15000,
      playbackCookie: 'base64...'
    },
    live: [{headSequenceNumber: 42, headTimeMs: 210000, postLiveDvr: false}],
    formats: [{itag: 251, endSegmentNumber: 120, durationUnits: 600,
      durationTimescale: 1}],
    contexts: [{type: 7, scope: 1, value: 'base64...', sendByDefault: true,
      writePolicy: 1}],
    contextPolicy: {start: [7], stop: [], discard: []}
  }
}
```

`mediaHeader({data})` receives a Base64 protobuf payload and returns a normalized media descriptor.
`headerId` and `itag` are required. Optional properties match the getters on `SabrMediaHeader`.
Media payload bytes never enter JavaScript.

`demandRoute(event)` chooses how to request a segment synchronously demanded by a reader. The event
contains `targetItag`, `targetSequenceNumber`, `targetStartMs`, `bufferedEdgeMs`, `createdAtMs`,
`nowMs`, `elapsedMs`, `responsesWithoutDemandedSegment`, and `recoveryCount`. It returns one route:
`STREAM`, `REWIND`, `FORWARD`, `RECOVER_REWIND`, `RECOVER_FORWARD`, or `RECOVER_MISSING`.
Recovery routes reset the corresponding request state before retrying; the Host records each
recovery and rejects inconsistent counters.

`demandResponse(event)` runs after a media-bearing SABR response that did not return the exact
demanded segment. Control-only responses remain owned by `response(event)` and do not consume the
demand omission budget. The event receives the same fields plus `segmentCount`, `targetTrackSegmentCount`,
`returnedSegmentsTruncated`, and up to 64 payload-free returned media identities shaped as
`{itag, sequenceNumber, startMs, durationMs}`. It returns `{outcome, retryDelayMs}`. Outcomes are
`CONTINUE`, `FAIL_REPEATED_TARGET_OMISSION`, and `FAIL_NO_TARGET_MEDIA`; retry delay is bounded to
0..5000 ms and must be zero for a terminal outcome. Server backoff intervals in which no request
was sent are not counted as omissions.

Both demand methods execute in the same policy object as request/response handling, so policy
session state, protocol revision, failover, signature validation, and diagnostics remain atomic.

The canonical signed payload is produced with `new SabrScriptPolicy(revision, validFromMs,
validUntilMs, source).serialize()`. Sign those exact bytes with Ed25519, then publish a single UTF-8
JSON document using `SabrScriptPolicyDocument.encode(policy, signature)`:

```json
{
  "format": 1,
  "revision": 1001,
  "validFromMs": 1784390400000,
  "validUntilMs": 1792166400000,
  "source": "function createSabrPolicy(sabr) { /* ... */ }",
  "signature": "base64-encoded Ed25519 signature"
}
```

The client reconstructs the canonical payload from the JSON fields before signature verification,
so whitespace and object-key order in the delivery document are not security-sensitive. `format`,
`revision`, `validFromMs`, and `validUntilMs` must be exact JSON integers: fractional values,
scientific notation, strings, and values outside the signed 64-bit range are rejected rather than
coerced. Document versioning only describes the source envelope; protocol behavior remains
JavaScript rather than a second serialized DSL.
