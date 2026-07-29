package org.schabi.newpipe.extractor.services.youtube.sabr;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabrJavaScriptPolicyTest {
    private static final long NOW = 2_000_000L;
    private static final String SCRIPT =
            "function createSabrPolicy(sabr){return{describe:function(){return{}},"
            + "initialRequest:function(e){return{body:e.fallbackBody}}}}";

    @Test
    void signedJavaScriptPolicyRoundTrips() throws Exception {
        final KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final SabrScriptPolicy original = new SabrScriptPolicy(7, NOW - 1, NOW + 1, SCRIPT);
        final byte[] payload = original.serialize();

        final SabrScriptPolicy verified = SabrScriptPolicy.parseVerified(payload,
                sign(payload, keys), keys.getPublic(), NOW, 7);

        assertEquals(7, verified.getRevision());
        assertEquals(SCRIPT, verified.getSource());
        assertArrayEquals(payload, verified.serialize());
    }

    @Test
    void signedJavaScriptPolicyDocumentRoundTripsAndRejectsTampering() throws Exception {
        final KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final SabrScriptPolicy policy = new SabrScriptPolicy(7, NOW - 1, NOW + 1, SCRIPT);
        final byte[] payload = policy.serialize();
        final byte[] document = SabrScriptPolicyDocument.encode(policy, sign(payload, keys));

        final SabrScriptPolicyDocument.Parsed parsed = SabrScriptPolicyDocument.decode(document);
        final SabrScriptPolicy verified = SabrScriptPolicy.parseVerified(parsed.getPayload(),
                parsed.getSignature(), keys.getPublic(), NOW, 7);

        assertEquals(SCRIPT, verified.getSource());
        assertArrayEquals(payload, verified.serialize());

        final String tampered = new String(document, StandardCharsets.UTF_8)
                .replace("initialRequest", "tamperedRequest");
        final SabrScriptPolicyDocument.Parsed changed = SabrScriptPolicyDocument.decode(
                tampered.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> SabrScriptPolicy.parseVerified(
                changed.getPayload(), changed.getSignature(), keys.getPublic(), NOW, 0));
    }

    @Test
    void policyDocumentRejectsNonIntegralAndOutOfRangeMetadata() throws Exception {
        final KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final SabrScriptPolicy policy = new SabrScriptPolicy(7, NOW - 1, NOW + 1, SCRIPT);
        final byte[] payload = policy.serialize();
        final String document = new String(SabrScriptPolicyDocument.encode(
                policy, sign(payload, keys)), StandardCharsets.UTF_8);

        assertInvalidDocument(document.replace("\"format\":1", "\"format\":1.9"));
        assertInvalidDocument(document.replace("\"format\":1", "\"format\":1e0"));
        assertInvalidDocument(document.replace("\"revision\":7", "\"revision\":7.9"));
        assertInvalidDocument(document.replace("\"revision\":7", "\"revision\":7e0"));
        assertInvalidDocument(document.replace("\"validFromMs\":1999999",
                "\"validFromMs\":1999999.0"));
        assertInvalidDocument(document.replace("\"validUntilMs\":2000001",
                "\"validUntilMs\":2000001e0"));
        assertInvalidDocument(document.replace("\"revision\":7",
                "\"revision\":9223372036854775808"));
        assertInvalidDocument(document.replace("\"validFromMs\":1999999",
                "\"validFromMs\":-9223372036854775809"));
        assertInvalidDocument(document.replace("\"format\":1", "\"format\":\"1\""));
        assertInvalidDocument(document.replace("\"revision\":7", "\"revision\":true"));
        assertInvalidDocument(document.replace("\"validFromMs\":1999999",
                "\"validFromMs\":null"));
        assertInvalidDocument(document.replace("\"validUntilMs\":2000001",
                "\"validUntilMs\":\"2000001\""));

        SabrScriptPolicyDocument.decode(document.replace("\"revision\":7",
                "\"revision\":9223372036854775807").getBytes(StandardCharsets.UTF_8));
        SabrScriptPolicyDocument.decode(document.replace("\"validUntilMs\":2000001",
                "\"validUntilMs\":9223372036854775807").getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalidDocument(@Nonnull final String document) {
        assertThrows(IllegalArgumentException.class, () -> SabrScriptPolicyDocument.decode(
                document.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsTamperingRollbackAndExpiry() throws Exception {
        final KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] payload = new SabrScriptPolicy(7, NOW - 1, NOW + 1, SCRIPT).serialize();
        assertThrows(IllegalArgumentException.class, () -> SabrScriptPolicy.parseVerified(
                payload, sign(payload, keys), keys.getPublic(), NOW, 8));
        assertThrows(IllegalArgumentException.class, () -> SabrScriptPolicy.parseVerified(
                payload, sign(payload, keys), keys.getPublic(), NOW + 1, 0));
        final byte[] tampered = payload.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> SabrScriptPolicy.parseVerified(
                tampered, sign(payload, keys), keys.getPublic(), NOW, 0));
    }

    @Test
    void hostRejectsForgedRecoveryStateAndMultipleTerminalActions() {
        final SabrDecodedResponse decoded = new SabrDecodedResponse();
        final SabrSessionPolicy.ControlResponseEvent event =
                new SabrSessionPolicy.ControlResponseEvent(0, true,
                        SabrSessionPolicy.ControlMode.PUMP, decoded);
        final SabrSessionPolicy.State state = new SabrSessionPolicy.State(1, 0, 0, 0);

        assertThrows(IllegalStateException.class, () -> hostReturning(
                SabrSessionPolicy.Result.control(state, Arrays.asList(
                        new SabrSessionPolicy.Action(SabrSessionPolicy.ActionType.CONTINUE),
                        new SabrSessionPolicy.Action(SabrSessionPolicy.ActionType.RETRY)),
                        new SabrSessionPolicy.ControlDecision(0, null, null)))
                .evaluate(state, event));
        assertThrows(IllegalStateException.class, () -> hostReturning(
                SabrSessionPolicy.Result.control(state, Arrays.asList(
                        new SabrSessionPolicy.Action(
                                SabrSessionPolicy.ActionType.APPLY_RESPONSE_STATE),
                        new SabrSessionPolicy.Action(SabrSessionPolicy.ActionType.CONTINUE)),
                        new SabrSessionPolicy.ControlDecision(0, null, null)))
                .evaluate(state, event));
        assertThrows(IllegalStateException.class, () -> hostReturning(
                SabrSessionPolicy.Result.control(state,
                        Collections.singletonList(new SabrSessionPolicy.Action(
                                SabrSessionPolicy.ActionType.CONTINUE)),
                        new SabrSessionPolicy.ControlDecision(0, null, null),
                        SabrResponseStatePatch.builder().build()))
                .evaluate(state, event));
        assertThrows(IllegalStateException.class, () -> hostReturning(
                SabrSessionPolicy.Result.control(new SabrSessionPolicy.State(1, 2, 0, 0),
                        Collections.singletonList(new SabrSessionPolicy.Action(
                                SabrSessionPolicy.ActionType.CONTINUE)),
                        new SabrSessionPolicy.ControlDecision(0, null, null)))
                .evaluate(state, event));
        assertThrows(IllegalStateException.class, () -> hostReturning(
                SabrSessionPolicy.Result.control(state,
                        Collections.singletonList(new SabrSessionPolicy.Action(
                                SabrSessionPolicy.ActionType.CONTINUE)),
                        new SabrSessionPolicy.ControlDecision(0,
                                "https://example.invalid", null)))
                .evaluate(state, event));
    }

    @Test
    void builtinDemandPolicyRecoversEveryOmittedDemandedSegment() throws Exception {
        final BuiltinSabrSessionPolicy policy = new BuiltinSabrSessionPolicy();
        final SabrSessionPolicy.DemandState firstOmission =
                new SabrSessionPolicy.DemandState(1_000, 1_100, 1, 0);
        assertEquals(SabrSessionPolicy.DemandRoute.RECOVER_MISSING,
                policy.evaluateDemandRoute(new SabrSessionPolicy.DemandRouteEvent(
                        251, 7, 30_000, 25_000, firstOmission)));

        final SabrSessionPolicy.DemandResponseDecision retry =
                policy.evaluateDemandResponse(new SabrSessionPolicy.DemandResponseEvent(
                        251, 7, 30_000, 25_000, firstOmission,
                        2, 0, Collections.singletonList(
                        new SabrSessionPolicy.DemandReturnedSegment(398, 9,
                                30_000, 5_000)), false));
        assertEquals(SabrSessionPolicy.DemandOutcome.CONTINUE, retry.getOutcome());

        final SabrSessionPolicy.DemandState thirdOmission =
                new SabrSessionPolicy.DemandState(1_000, 1_300, 3, 1);
        assertEquals(SabrSessionPolicy.DemandOutcome.FAIL_REPEATED_TARGET_OMISSION,
                policy.evaluateDemandResponse(new SabrSessionPolicy.DemandResponseEvent(
                        251, 7, 30_000, 25_000, thirdOmission,
                        1, 0, Collections.singletonList(
                        new SabrSessionPolicy.DemandReturnedSegment(398, 10,
                                35_000, 5_000)), false)).getOutcome());
    }

    @Test
    void hostRejectsInvalidDemandEventsAndDecisions() {
        final SabrSessionPolicy.State state = new SabrSessionPolicy.State(1, 0, 0, 0);
        final SabrSessionPolicy policy = new SabrSessionPolicy() {
            @Nonnull
            @Override
            public Result evaluate(@Nonnull final State ignoredState,
                                   @Nonnull final Event ignoredEvent) {
                return Result.request(state, ActionType.SEND_FOLLOW_UP_REQUEST, new byte[]{1});
            }

            @Nonnull
            @Override
            public DemandResponseDecision evaluateDemandResponse(
                    @Nonnull final DemandResponseEvent event) {
                return new DemandResponseDecision(DemandOutcome.CONTINUE,
                        MAX_DEMAND_RETRY_DELAY_MS + 1);
            }
        };
        final SabrSessionPolicyHost host = new SabrSessionPolicyHost(policy, null);
        assertThrows(IllegalArgumentException.class, () -> host.evaluateDemandRoute(
                new SabrSessionPolicy.DemandRouteEvent(0, 1, 0, 0,
                        new SabrSessionPolicy.DemandState(1, 1, 0, 0))));
        assertThrows(IllegalStateException.class, () -> host.evaluateDemandResponse(
                new SabrSessionPolicy.DemandResponseEvent(251, 1, 0, 0,
                        new SabrSessionPolicy.DemandState(1, 2, 1, 0),
                        1, 0, Collections.singletonList(
                        new SabrSessionPolicy.DemandReturnedSegment(398, 2,
                                0, 5_000)), false)));
    }

    @Test
    void normalizedPatchUpdatesRequestLiveAndFormatState() throws Exception {
        final JsonArray formats = new JsonArray();
        final JsonObject audio = new JsonObject();
        audio.put("itag", 140);
        audio.put("mimeType", "audio/mp4");
        audio.put("lastModified", "1");
        audio.put("approxDurationMs", "100000");
        formats.add(audio);
        final JsonObject video = new JsonObject();
        video.put("itag", 134);
        video.put("mimeType", "video/mp4");
        video.put("lastModified", "2");
        video.put("approxDurationMs", "100000");
        formats.add(video);
        final java.util.List<YoutubeSabrFormat> parsed =
                YoutubeSabrFormat.fromAdaptiveFormats("video", formats);
        final YoutubeSabrStreamState streamState = new YoutubeSabrStreamState(
                parsed.get(0), parsed.get(1));
        final byte[] cookie = new byte[]{7, 8, 9};
        final SabrResponseStatePatch patch = SabrResponseStatePatch.builder()
                .setNextRequestPolicy(SabrNextRequestPolicy.normalized(
                        9_000, 10_000, 2_000, 0, 3_000, 4_000, cookie, "video"))
                .addLiveMetadata(SabrLiveMetadata.normalized(
                        null, 77, 123_000, -1, "video", false,
                        -1, -1, -1, -1, -1))
                .addFormatMetadata(SabrFormatInitializationMetadata.normalized(
                        "video", 140, 1, null, 100_000, 20,
                        "audio/mp4", -1, -1, -1, -1, -1, 100, 1))
                .build();

        streamState.ingest(patch);

        assertArrayEquals(cookie, streamState.getPlaybackCookie());
        assertEquals(9_000, streamState.getNextRequestPolicy().getTargetAudioReadaheadMs());
        assertTrue(streamState.isLive());
        assertEquals(77, streamState.getLiveHeadSequenceNumber());
        assertEquals(20, streamState.getEndSegment(parsed.get(0)));
    }

    private static SabrSessionPolicyHost hostReturning(final SabrSessionPolicy.Result result) {
        return new SabrSessionPolicyHost((state, event) -> result, null);
    }

    private static byte[] sign(final byte[] payload, final KeyPair keys) throws Exception {
        final Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(payload);
        return signer.sign();
    }
}
