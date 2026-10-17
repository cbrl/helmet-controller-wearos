package com.cbrl.pixelblaze.helmet

import org.json.JSONArray
import org.json.JSONObject

/** A getVars reply of a v4 pattern that shows [state]. */
fun patternVars(
    state: HelmetState,
    session: Int,
    messageRev: Int = 0,
    layout: HelmetLayout = HelmetLayout.Default,
): JSONObject = JSONObject(HelmetProtocol.stateVars(state, messageRev))
    .put(HelmetProtocol.VAR_VERSION, HelmetProtocol.VERSION)
    .put(HelmetProtocol.VAR_SEGMENT_COUNT, HelmetSegment.entries.size)
    .put(HelmetProtocol.VAR_EXPECTED_PIXELS, 496)
    .put(HelmetProtocol.VAR_LAYOUT_VALID, 1)
    .put(HelmetProtocol.VAR_MASTER_FADE, 0.4)
    .put(HelmetProtocol.VAR_SESSION, session)
    .put(
        HelmetProtocol.VAR_LAYOUT,
        JSONArray().apply { layout.segments.forEach { put(it.size); put(it.width); put(it.height) } },
    )

/** The variable names in a setVars object. Android's JSONObject has no keySet(). */
fun JSONObject.keyNames(): Set<String> = keys().asSequence().toSet()
