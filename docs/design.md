# LUMI Travessias — visual and interaction contract

## Direction

The v0.3.0 game replaces the shooter with a river-crossing drawing puzzle. Preserve the minimal line language, Android portrait shell and encrypted-key setup. The visual concept is a full primary game screen, with a midnight canvas, mint line character/riverbanks, gold drawings and blue water. Production art is code-native vector animation, matching the user's request for lines rather than bitmap sprites.

Colors: background #07121e, foreground #ecf5fa, mint #85f4cf, gold #ffe5a1, muted #9db4cb, border #294358. Rounded sans-serif fallback, 34–46px primary heading, 15px body, primary controls ≥50px, header targets 42px. Native Android system-bar insets remain outside the WebView.

Primary composition: LUMI wordmark and controls; two-line “Como atravessar?”; short instruction; river scene with character and goal star; conditions and challenge source; large dashed drawing pad; undo/clear; one mint Experimentar button. This is the game surface, not a landing page. Source labels are deliberately visible to address the previous integration's invisibility.

## Loop

1. Read/see/hear the challenge. Drawing examples teach bridge, boat and jump arrow.
2. Draw with one finger. Strokes stay local until Experimentar. Export only the strokes as a black-on-white 512×320 PNG.
3. Show recognition in progress. A valid Decisions choice triggers a distinct 3.4-second animation. Unclear keeps the canvas editable.
4. Show success or a concrete retry explanation. Lumi remains safe on failed attempts. Permit another drawing or a next challenge.
5. Save the bounded outcome and request the next Agents challenge. Show preparation and offer a clearly labelled local alternative while waiting.

## Deterministic meaning

Bridge: a deck connects the banks and Lumi walks across. Boat: a hull and sail carry Lumi on calm water; fast water returns the character. Jump: an arc crosses a narrow river; wide rivers or a backpack prevent success. Width/current/backpack come from the challenge, not from the classifier. Interpretation and feasibility are separate responsibilities.

The child’s exact line geometry is not a collision mesh: the recognized category maps to a predefined local animation. The original drawing remains visible during the animation. No generated code is executed in the APK.

## States and honesty

- Drawing: empty canvas, editable strokes, clear instructions, optional examples.
- Recognizing: blocking pad overlay, one in-flight classification, no blind retry.
- Animating: local physics-independent scene animation, drawing visible below.
- Result: explanation, source/confidence, optional manual correction, retry/next.
- Waiting: agent preparation, optional local challenge; late responses cannot erase an active drawing.
- Manual fallback: explicit player choice; never labelled as AI recognition.

The parent gate leads to a service-by-service diagnostic panel, recent attempt history, counts and native connection setup. No free-text child/model conversation exists. Reduced effects and native Portuguese TTS improve accessibility. Drawings/progress are not interchangeable with credentials: Vault is empty, Keystore holds the API key.
