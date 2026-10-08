# Decision Flights design

Direction: a white, readable Android travel tool with orange actions. Primary
reference: the generated Decision mobile concept, 2026-10-08. The reference is a
design aid; no bitmap interface is shipped.

Tokens: background #ffffff; text #16181b; muted #777e87; border #e0e3e7;
accent #ff5b2a; soft accent #fff1eb. System sans. Heading 38px/1.08 on small
phones and 42px on wider surfaces, supporting text 17px/1.35, input values
19px, labels 11px, buttons 16px. Gutter 20px on
small phones, 28px on wider phones. Form radius 20px, button radius 16px.

Primary screen: wordmark and settings; two-line heading; supporting copy;
origin/destination and swap; dates; passengers/cabin; search button; hosting
caption; preferences; history. No decorative imagery, price promises, or
performance claims. All displayed fare data must come from a completed agent
turn. Preview mode does not fabricate flight results.

Continuation states use the same tokens: observable browser activity with its
latest screenshot; native website-origin consent; verified offer rows; source
and retrieval time; same-session refinement; connection and resource details.
Settings and consent are sheets. The native key-entry dialog keeps credentials
outside JavaScript. All controls have 44px minimum touch targets. Reduced
motion is supported. Android system and keyboard insets are applied natively.

Required copy: decision; Sua próxima viagem.; Você escolhe o destino. A OpenAI
faz a busca.; DE ONDE; PARA ONDE; IDA; VOLTA; Viajantes; Buscar passagens;
Navegador hospedado na OpenAI; O que importa para você?; Últimas buscas;
Suas viagens começam aqui.

Intentional adaptations: dates use upcoming calendar dates; travelers is a
working selector; a smaller device can scroll. Progress, result and error
states extend the same design to support the required real API workflow.

## Visual verification · 2026-10-08

Reference concept:
`/workspace/scratch/7dd96745b05b/generated_images/exec-ff1391b4-3e12-4f50-9dcd-b1247ef48bd3.png`.
Latest rendered evidence:
`/workspace/scratch/7dd96745b05b/deliverables/Decision-Flights-preview.png`.
Both were inspected using `view_image` at original resolution in the final QA
pass. The implementation screenshot is Chromium rendering of actual app assets,
not a generated mockup and not a screenshot from a physical Android device.

The cloud Browser was tried first; it could not reach the local server
(`ERR_CONNECTION_REFUSED`). Local Playwright/Chromium was therefore used. The
final capture matches the concept's 863 × 1823 physical pixels using a
393 × 830 CSS viewport and device scale factor 863/393. Functional checks also
covered widths 320, 360, 540 and 1280. Android native insets and key-entry flow
were compiled but have not been exercised on a physical device.

| Comparison | Reference / render evidence | Repair or intentional adaptation |
|---|---|---|
| Copy and hierarchy | Wordmark, two-line heading, route → dates → travelers → search → preferences → history | Above-fold copy diff passed: no added or reordered product copy; dates use the current calendar |
| Typography | Heavy headline and wordmark, quiet supporting copy, clear form values | Reduced headline 42 → 38px and wordmark 27 → 23px after image comparison; medium preference/history headings now match the quieter reference |
| Palette and assets | White background, dark type, orange plane and primary action, outline icons | Exact #fff / #16181b / #ff5b2a tokens; a flat primary color intentionally replaces generator texture; all controls are live HTML/SVG |
| Spacing and containers | Rounded route block, paired dates, open preferences/history sections | Tightened header, row padding and vertical rhythm; preserved the container model without decorative cards |
| Icons and touch | Plane, departure, arrival, calendar, travelers, swap, arrow, settings, note, clock | Shared SVG symbols with round joins; increased swap hit area from 40 to 44px; native calendar and selection behavior |
| Responsive behavior | A portrait phone surface with readable inputs and prominent action | No horizontal overflow at five tested widths; shorter phones scroll, including a small footer scroll at the reference-size viewport |
| Workflow and motion | Search action and editable travel fields | Swap, one-way mode, travelers, connection gate, permission, sorting, refinement, cancel and close verified; reduced motion supported |

No material visual mismatches remain within these stated adaptations. The
implementation was faithfully verified against the reference direction. Font
glyphs can differ between Android's installed sans font and desktop Chromium;
the interface deliberately avoids downloading fonts. Form dates and browser
permission/connection sheets are functional additions required by the app.

31 automated interface checks passed. The bridge used offline test fixtures
for downstream states; no fixture fares or seeded results are shipped. 47
contract/lifecycle checks passed separately. Live flight retrieval, provider
latency, billing and physical-device installation remain unverified until an
authorized OpenAI key is connected in the installed app.
