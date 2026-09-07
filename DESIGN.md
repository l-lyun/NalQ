---
version: alpha
name: NalQ SEED-Aligned Study Workspace
description: A calm, document-like learning interface whose runtime foundation and components come from SEED Design.
omitted:
  - section: colors
    reason: SEED semantic color tokens and theme resolution are the authoritative runtime source; this document records roles, not duplicate literal values.
  - section: typography
    reason: SEED text styles remain authoritative for size, line height, and weight; NalQ's approved Pretendard-first family and platform boundaries are recorded below without duplicating font assets or implementation details.
  - section: spacing
    reason: SEED dimension and semantic spacing tokens are authoritative and are referenced in the Layout section.
  - section: rounded
    reason: SEED component recipes and radius tokens are authoritative and are referenced in the Components section.
  - section: components
    reason: Current SEED React documentation and installed packages are authoritative; component mappings below are guidance, not copied recipe tokens.
---

# NalQ Design System

> Status: design baseline draft
>
> Format: Stitch `DESIGN.md` compatible headings with NalQ's nine-section extension
>
> Foundation source: SEED Rootage 2.6.0 and current SEED React documentation, verified 2026-08-19

This file defines how NalQ should feel, arrange information, and choose SEED primitives. It is not a copy of SEED token values and must not become a second token source. When a SEED recipe or token changes, the installed SEED package and current official documentation win.

## Overview

### Visual Theme & Atmosphere

NalQ should feel like a well-organized study desk in clear daylight: calm enough for long reading, structured enough to make the next learning action obvious, and warm without looking decorative or childish. The interface is content-first and document-like. Whitespace, type hierarchy, and short lists do most of the grouping; containers and shadows are supporting tools.

The visual rhythm is mostly neutral. SEED brand color appears where the user should act, not as a page-wide decoration. Positive, critical, warning, and informative colors retain their semantic meanings. AI generation may use SEED's `magic` loading treatment, but AI output must still be labeled in words and must never look authoritative only because of color.

NalQ is an application, not a Notion marketing-site replica. Do not reproduce Notion's proprietary font, blue, warm-white HEX values, sticker palette, pill-everywhere styling, or dark promotional hero. Preserve the useful design ideas instead: a quiet document canvas, generous grouping space, restrained elevation, and one unmistakable next action.

### Product goals that shape the interface

- Help the user move from imported material to recall practice and feedback with minimal hesitation.
- Make the highest-priority next learning action more prominent than statistics or feature discovery.
- Keep the experience consistent after Notion, file, or pasted-text import.
- Expose AI uncertainty, original-source access, recovery actions, and ambiguous grading.
- Keep learning valuable without relying on unconfirmed ranking, economy, character, or social features.

### Information hierarchy

1. Current task or recommended next learning action
2. Context needed to understand that action: material, progress, due review, or source
3. Supporting actions and alternatives
4. History, metadata, and future-facing features

### Confirmed product constraints

- Signed-in navigation has three destinations: Home, Learning, and Profile.
- Home prioritizes one contextual next action.
- Material import, question creation, solving, and review form one learning journey.
- Immersive quiz flows may hide global tabs, but exit and back behavior must remain predictable.

### Design decisions in this document

- Use SEED semantic tokens and component recipes instead of Notion values or local look-alike primitives.
- Use Pretendard as NalQ's approved web and WebView-first Korean font family while preserving SEED text styles for size, line height, and weight.
- Use a mobile-first, single-column study flow; introduce multiple columns only when they improve comparison or scanning.
- Prefer lists and whitespace over repeating dashboard cards.
- Keep one prominent solid CTA per screen or local task region.
- Remain theme-ready through semantic tokens; do not encode a light-only palette in this file.

### Open decisions and exceptions

- NalQ's proprietary illustration, character, and decorative asset language is not defined. Do not invent a Notion-like sticker palette.
- A verified SEED bottom-navigation React component was not found in the current Docs MCP listing. The three-tab product structure remains confirmed, but its implementation primitive must be checked against the installed package before coding.
- Exact desktop content max-width and whether the product exposes dark appearance remain implementation/design review decisions. Do not turn example widths into global contracts.

## Colors

### Color Palette & Roles

Use semantic token names in design and implementation. Never copy the token's resolved light-theme HEX into product code: the same token resolves differently by theme.

| Role | SEED token | NalQ use |
| --- | --- | --- |
| App basement | `bg.layerBasement` | Recessed app background behind primary surfaces |
| Primary surface | `bg.layerDefault` | Main page, reading, list, and form surface |
| Floating surface | `bg.layerFloating` | Overlay or genuinely floating content; normally owned by component recipes |
| Quiet grouping surface | `bg.neutralWeak` | Low-emphasis section, selected grouping, or gentle contrast |
| Primary text | `fg.neutral` | Headings, body, essential values |
| Secondary text | `fg.neutralMuted` | Supporting copy and secondary values |
| Tertiary text | `fg.neutralSubtle` | Metadata and low-priority labels that still meet contrast requirements |
| Placeholder | `fg.placeholder` | Input placeholder only, never a replacement for a label |
| Brand foreground | `fg.brand` | Brand-emphasis text or icon when the semantic role is truly brand-related |
| Brand action | `bg.brandSolid` | `ActionButton variant="brandSolid"`; primary learning action |
| Brand pressed | `bg.brandSolidPressed` | Pressed state supplied by the component recipe |
| Neutral solid action | `bg.neutralSolid` | General CTA through the component recipe |
| Disabled | `bg.disabled`, `fg.disabled` | Disabled state through a SEED recipe; accompany with semantic disabled behavior |
| Positive | `fg.positive`, `bg.positiveWeak`, `stroke.positiveWeak` | Correct answer, completed operation, recoverable success |
| Critical | `fg.critical`, `bg.criticalWeak`, `stroke.criticalSolid` | Error, destructive action, invalid input |
| Warning | `fg.warning`, `bg.warningWeak`, `stroke.warningWeak` | Risk, ambiguity, or action that needs attention |
| Informative | `fg.informative`, `bg.informativeWeak`, `stroke.informativeWeak` | Neutral guidance and explanatory system status |
| Subtle divider | `stroke.neutralSubtle` | Quiet separation where whitespace alone is insufficient |
| Stronger field/boundary | `stroke.neutralWeak` | Input and surface boundary when owned by a recipe |
| Focus | `stroke.focusRing` | Keyboard focus indication; never remove without an equivalent |

### Color behavior

- Let component recipes choose contrasting foreground colors on solid backgrounds.
- Use brand color for the recommended learning action, important brand link, or active brand signal. Do not use it as decoration.
- Use semantic positive/critical/warning/informative pairs for status; pair every color signal with text, icon, or structure.
- Use palette tokens only when an approved illustration or data-visualization specification requires a fixed hue. Product UI defaults to semantic tokens.
- Do not use the deprecated `bg.layerFill`. Choose `bg.layerDefault` for a surface or `bg.neutralWeak` for a quiet grouping role.
- Do not create a custom warm canvas HEX. The calm document feeling comes from `bg.layerBasement` / `bg.layerDefault`, spacing, and hierarchy while preserving theme behavior.

## Typography

### Typography Rules

Use SEED `Text` and its `textStyle` prop. A text style combines font size, line height, and weight; prefer it over setting each property independently. Use scalable `t*` styles by default so user font scaling is respected. `Static` styles are exceptions for cases proven to require fixed typography, not a visual shortcut. The NalQ font-family decision changes glyph design and fallback behavior, not the SEED text scale or semantic hierarchy.

| NalQ role | SEED text style | Typical use |
| --- | --- | --- |
| Display statement | `t14Bold` | Rare onboarding or completion statement; not routine app chrome |
| Screen title | `t9Bold` | Primary application page title; `t10Bold` may be used for a wider layout when needed |
| Major section title | `t7Bold` | Top-level learning section |
| Card/list group title | `t6Bold` | Content group or result section title |
| Item title | `t5Bold` or `t5Medium` | Material title, quiz title, strong row title; preserve the native List recipe |
| Lead body | `t6Regular` | Short explanatory intro |
| Default body | `t5Regular` | Reading copy, form copy, question text baseline |
| Emphasized body | `t5Medium` or `t5Bold` | Inline emphasis or value, not whole paragraphs |
| Compact body | `t4Regular` | Dense list detail and controls where recipe allows |
| Metadata | `t3Regular` or `t3Medium` | Dates, counts, supporting labels |

The approved [UI density scope](docs/ux/screen-ui-density.md) changes text styles and spacing in application screens. Preserve existing font family, component variants, colors, outer borders, radii, and elevation. Keep primary CTA and mobile input sizes, safe-area clearances, and navigation targets. Public landing and onboarding display typography retain their screen-specific purpose.

### SEED scale reference

SEED Rootage provides scalable `t1`–`t14` sizes equivalent to 11, 12, 13, 14, 16, 18, 20, 22, 24, 26, 28, 32, 40, and 48px at the default root size, with paired line-height tokens. Available weights are `regular` 400, `medium` 500, and `bold` 700. This reference explains the hierarchy; implementation should still use `textStyle`, not recreate the numeric values.

### Type principles

- Use weight and scale to create hierarchy; do not recreate Notion's custom negative letter spacing.
- Keep Korean body text at `t5Regular` when space allows. Do not shrink important learning copy to make a layout fit.
- Limit the number of strong bold headings visible at once. The user's next action and current question should win.
- Use real heading elements through the component's semantic `as` support where available; visual size does not determine document outline.
- Never use placeholder text as the only field label.
- Truncation is acceptable for repetitive list metadata, but learning material titles and question content should wrap before being truncated. If `maxLines` is used, provide a route to the full text.

### Font-family boundary

Pretendard is the approved NalQ font direction for Korean reading quality and consistent web-based UI across platforms. This is an NalQ design decision, not a SEED Rootage foundation fact.

SEED officially recommends its cross-platform system-font stack. NalQ intentionally overrides only the font-family for Korean reading quality and web/WebView consistency; SEED text styles remain authoritative for font size, line height, and weight.

- Web and WebView content use `Pretendard Variable` first, followed by a Korean-capable platform system sans fallback and then generic system sans. Do not rely on a Latin-oriented system stack as the only Korean fallback.
- WebView content uses the same font assets delivered by the web bundle. The native shell must not load a second independent font for content rendered inside that WebView.
- Prefer locally bundled, reproducible font assets so the core interface remains usable offline and does not depend on a third-party CDN. A CDN is not a design requirement.
- Native-only UI outside the WebView may bundle the same font after the app team reviews binary size, startup cost, platform behavior, and licensing. Until that implementation decision is made, native UI may use its Korean-capable system font; visual and line-break differences must still be reviewed.
- Keep SEED `textStyle` values authoritative for font size, line height, and weight. Font loading must provide the weights used by the approved styles without remapping the typography scale.
- Do not reintroduce `NotionInter`, custom Inter tracking, or screen-specific font families. Font family is an application-wide decision.
- Exact packages, versions, font file paths, subsetting, preload strategy, fallback declarations, and bundler configuration belong in the relevant application TRD and implementation, not in this design baseline.

## Components

### Component Stylings

Use the current SEED React component when it owns the meaning, states, and accessibility behavior. Use `Box`, `VStack`, `HStack`, `Flex`, and `Grid` for composition; do not create a local component merely to imitate a SEED control.

| Need | Verified SEED component or composition | NalQ rule |
| --- | --- | --- |
| Primary action | `ActionButton` `brandSolid` or `neutralSolid`, usually `size="large"` | One prominent solid CTA per screen/task region; reserve `brandSolid` for the core learning action |
| Supporting action | `ActionButton` `neutralWeak`, `neutralOutline`, `brandOutline`, or `ghost` | Keep visually subordinate; do not pair outline variants with solid variants against SEED guidance |
| Destructive action | `ActionButton` `criticalSolid` | Only for irreversible delete/reset after appropriate confirmation |
| Icon-only action | `ActionButton layout="iconOnly"` | Exceptional; always supply an accessible label |
| Single-line input | `TextField` + `TextFieldInput` | Use label, description, invalid, and error message states; `outline` is default |
| Long text input | `TextField` + `TextFieldTextarea` | Use for pasted learning content and answers; preserve visible label and error recovery |
| File import | `AttachmentField` / current attachment input recipe | Validate current docs and accepted file behavior before implementation |
| Content row | `List`, `ListItem`, `ListHeader`, `ListDivider` | Default for recent materials, settings, and learning history |
| Navigable row | `ListButtonItem` or `ListLinkItem` | Make the entire row interactive when it represents one destination/action |
| Category/filter switching | `ChipTabs` | Use for peer content views; add `ScrollFog` when labels overflow horizontally |
| Immediate exclusive switch | `SegmentedControl` | Use for a small, stable set that immediately changes the view |
| Form choice | `RadioGroup`, `SelectBox`, or `Select` | Use for quiz type, difficulty, and explicit form values according to option count |
| Context-preserving mobile modal | `BottomSheet` | Source selection or action list while retaining current context |
| Rich wide-screen modal | `Dialog` | Form or scrollable content; use `AlertDialog` for confirmation/warning only |
| Loading placeholder | `Skeleton` | `neutral` for ordinary data, `magic` only while AI is actively generating |
| Empty/result/full-region failure | `ResultSection` | Explain outcome and offer the most useful recovery or next action |
| Page-level status | `PageBanner` | Put important page-wide status near the top; choose semantic tone |
| Transient confirmation | `Snackbar` | Short-lived feedback such as saved/copied; not for information needed to continue |
| In-progress operation | `ProgressCircle` | Use when a determinate content skeleton is unsuitable; pair with status text |
| App-specific content container | `Box`/`VStack`/`Grid` composition | Prefer flat surface and spacing; there is no verified generic SEED `Card` requirement |

### Interaction and state rules

- Design enabled, pressed, focus-visible, loading, disabled, error, empty, success, and permission-limited states before treating a screen as complete.
- `ActionButton loading` does not automatically mean disabled. Add `disabled` only when repeat interaction must be prevented.
- `TextField` uses `large` at all viewport widths by default. `medium` is allowed only at `lg` and above for precise pointer environments; `responsive` may be used when both environments are supported.
- Use `TextField variant="underline"` only when a screen contains one input and the lower visual boundary is appropriate. Otherwise use `outline`.
- `BottomSheetTrigger` and `DialogTrigger` should preserve their built-in dialog relationships. Do not replace them with click handlers that lose `aria-haspopup` / `aria-expanded` behavior.
- Correctness, AI uncertainty, and error states must include words. Do not encode them using green, magic, or red alone.

### Shapes

SEED radius tokens are `r0_5`, `r1`, `r1_5`, `r2`, `r2_5`, `r3`, `r3_5`, `r4`, `r5`, `r6`, and `full`. Use a component's recipe radius whenever a SEED component exists.

- `r2`–`r3`: quiet app-specific panels or grouped content when a container is necessary.
- `r3`–`r4`: larger media or result wells, used sparingly.
- `full`: avatars, circular controls, and recipes designed as pills; never apply globally to fields and containers.
- Do not encode meaning by radius or mix several radii in one local component family.

## Layout

### Layout Principles

NalQ is mobile-first and vertically progressive. A user should be able to identify the page, understand the current learning context, and see the next action without scanning a dashboard grid.

### SEED spacing scale

| Token | Resolved size | NalQ role |
| --- | ---: | --- |
| `x1` | 4px | Micro alignment only; not a page gap |
| `x2` | 8px | Icon-label and tightly related inline content |
| `x3` | 12px | Default component-to-component gap |
| `x4` | 16px | Standard content padding and mobile gutter basis |
| `x5` | 20px | Navigation-to-title rhythm |
| `x6` | 24px | Content group padding or title-to-section separation |
| `x8` | 32px | Major section gap |
| `x10` | 40px | Large state/hero internal separation |
| `x12` | 48px | Sparse page-section separation |
| `x14` | 56px | Screen-bottom spacing token basis |
| `x16` | 64px | Rare major separation; avoid stacking repeatedly |

Prefer semantic aliases where the role matches: `spacingX.globalGutter`, `spacingY.componentDefault`, `spacingY.navToTitle`, `spacingY.betweenText`, and `spacingY.screenBottom`.

### Composition rules

- Page shell: `bg.layerBasement` behind one primary `bg.layerDefault` content plane when visual separation is useful.
- Screen header: navigation/chrome, then `spacingY.navToTitle`, then one clear screen title and optional supporting line.
- Main flow: `VStack` and a single reading column. Question text, answer controls, feedback, and next action remain in one predictable vertical sequence.
- Repeated content: use `List` before introducing individual cards. Use dividers only when spacing cannot communicate grouping.
- Comparison: use `Grid` for genuinely comparable choices or summaries; collapse to one column at `base` and expand only at a documented breakpoint.
- Home: next-action region first, followed by review summary and recent materials. Statistics do not outrank the next action.
- Learning: import entry points and material collection belong in the same destination but can be separated into clear sections.
- Quiz: suppress unrelated navigation when needed, keep progress and exit predictable, and maintain a stable bottom action region without covering content.

### Whitespace philosophy

Whitespace is the main grouping device. A border is secondary; a shadow is exceptional. Do not place every section in a raised card. When a section feels unclear, first improve title, order, spacing, and alignment before adding chrome.

## Elevation & Depth

### Depth & Elevation

Use semantic layer colors before shadows. Let SEED overlay components own their elevation recipe.

| Level | SEED treatment | Use |
| --- | --- | --- |
| 0 — Document | `bg.layerDefault`, no shadow | Default page, lists, reading and quiz content |
| 1 — Local lift | `shadow.s1` | Rare floating helper or app-specific element that must separate from nearby content |
| 2 — Overlay | `shadow.s2` | Popover/dialog-like custom surface only when a SEED overlay cannot own it |
| 3 — High overlay | `shadow.s3` | Exceptional topmost surface; never routine cards |

### Elevation rules

- Do not reproduce Notion's multi-stop custom box shadows.
- Do not combine a strong border and a strong shadow to force hierarchy.
- Bottom sheets, dialogs, menus, and snackbars use their SEED recipe rather than locally assigned shadows.
- Keep surfaces flat in dense learning and quiz contexts so feedback and actions, not decoration, hold attention.
- Dark appearance must use the same semantic layer relationships; do not reverse-engineer light colors with opacity.

## Do's and Don'ts

### Do

- Do make the next learning action the most prominent control.
- Do use SEED semantic tokens and current component recipes as the source of truth.
- Do use `Text` styles instead of recreating size, line height, and weight combinations.
- Do use the approved Pretendard-first family for web and WebView content with a Korean-capable system fallback.
- Do prefer `ListButtonItem` / `ListLinkItem` for one-action rows and reserve custom containers for unique content structures.
- Do show original-source access and uncertainty alongside AI-generated questions and ambiguous grading.
- Do provide loading, empty, partial error, full error, success, disabled, and permission-limited states.
- Do keep visible focus, semantic labels, heading order, and keyboard behavior.
- Do use `PageBanner` for important page-wide messages and `ResultSection` for a region or page outcome.
- Do verify exact component props, icons, and package imports in current SEED Docs before implementation.

### Don't

- Don't copy Notion HEX values, `NotionInter`, sticker colors, tracking, or shadow stacks.
- Don't make a third-party font CDN a requirement for rendering core product UI.
- Don't hardcode resolved light-theme color values; doing so breaks theme semantics.
- Don't use palette colors for ordinary application chrome.
- Don't use multiple brand-solid or neutral-solid CTAs at the same hierarchy on one screen.
- Don't create a generic local `Card`, `Button`, `Input`, `Tabs`, `Dialog`, or `Toast` when a verified SEED component fits.
- Don't use deprecated `InlineBanner`, `ErrorState`, or `bg.layerFill`; use `PageBanner`, `ResultSection`, and current layer/neutral tokens.
- Don't use `Static` typography to prevent user font scaling unless an accessibility-reviewed exception exists.
- Don't shrink touch targets or important Korean copy to preserve a desktop composition.
- Don't communicate correct/incorrect, risk, selection, or AI status through color alone.
- Don't introduce game-economy, character, ranking, or decorative sticker UI before those product and asset rules are approved.

## Responsive Behavior

SEED is mobile-first. Values set at a breakpoint continue into wider viewports unless overridden.

| Breakpoint | Min width | NalQ behavior |
| --- | ---: | --- |
| `base` | 0px | Single-column WebView/app layout, full-width primary action, bottom-tab context |
| `sm` | 480px | Preserve single flow; allow slightly wider local controls and media |
| `md` | 768px | Two columns only for comparison, supporting summary, or material browsing; dialogs become appropriate |
| `lg` | 1280px | Expanded desktop workspace; dense pointer-only control sizes may be considered where SEED permits |
| `xl` | 1440px | Increase outer whitespace, not reading-line length or the number of competing actions |

### Adaptation strategy

- Prefer responsive props on `Box`, `Flex`, `Grid`, `VStack`, and `HStack`, for example `padding={{ base: "x4", md: "x6", xl: "x8" }}`.
- Prefer CSS-driven responsive props to `useBreakpoint`; use hooks only when behavior, not presentation, must change.
- Collapse grids to one column at `base`; do not change the semantic order when columns appear.
- Keep quizzes and long reading in a centered single column even on wide screens. Additional width becomes outer whitespace or an optional supporting pane.
- Let long Korean titles wrap. Horizontal scrolling is acceptable only for components designed for it, such as `ChipTabsCarousel` with `ScrollFog`.
- Maintain at least a 44 by 44 CSS-pixel interactive target where a SEED recipe does not already provide a larger target.
- Respect viewport and WebView safe areas. Fixed bottom actions and the three-tab navigation must not overlap each other, the keyboard, or `spacingY.screenBottom` content clearance.
- At large text settings, allow containers and rows to grow vertically; never clip labels, answers, feedback, or error recovery actions.
- When immersive quiz mode hides tabs, preserve a labeled exit/back action and announce route or result changes appropriately.

## Agent Prompt Guide

### Fast reference

```text
Mood: calm document-like study workspace; neutral, spacious, action-led
Primary surface: bg.layerDefault over bg.layerBasement
Primary text: fg.neutral; support: fg.neutralMuted / fg.neutralSubtle
Font: Pretendard Variable first for web/WebView, then Korean-capable system sans fallback
Primary learning action: ActionButton brandSolid, one prominent solid CTA
Default body: Text t5Regular; app screen title: t9Bold; section title: t7Bold
Layout: mobile-first VStack/List; Grid only for real comparison
Spacing: semantic aliases first, then x2/x3/x4/x6/x8
Radius: component recipe first; app panels usually r2-r3
Depth: flat first; SEED overlay recipe; s1-s3 only for justified custom surfaces
States: Skeleton, ResultSection, PageBanner, Snackbar, component-native disabled/error/loading
Never: hardcoded HEX, Notion font/tracking/shadows, palette-colored chrome, invented SEED props
```

### Ready-to-use generation prompt

```text
Design an NalQ screen from its approved feature, flow, and screen specification.

Keep the experience like a calm, well-organized study desk: content-first, document-like,
and generous with whitespace. Put one contextual next learning action above statistics and
secondary features. Use the NalQ DESIGN.md hierarchy and responsive rules.

Use current SEED React components and semantic tokens. Verify component names, props,
deprecation status, icons, and Rootage tokens in the official SEED Docs MCP before writing
implementation code. Prefer Text, Box/VStack/HStack/Grid, ActionButton, TextField, List,
ChipTabs/SegmentedControl, BottomSheet/Dialog, Skeleton, ResultSection, PageBanner, and
Snackbar when their documented role matches.

Use the approved Pretendard-first font family for web and WebView content while keeping
SEED textStyle values authoritative. Preserve a Korean-capable system fallback and do not
make a third-party CDN necessary for the core interface.

Do not copy Notion HEX values, typography, stickers, or shadows. Do not hardcode resolved
token values or invent a SEED API. Preserve visible focus, semantic labels, heading order,
keyboard behavior, large text, safe areas, and loading/empty/error/success/disabled states.
If SEED has no verified matching component, first try a composition of SEED layout and
foundation primitives, then document the custom exception and its accessibility impact.
```

### Agent verification order

1. Read `docs/README.md`, then the smallest relevant product foundation, PRD, and UX documents.
2. Confirm which requirements are approved, assumed, or still open.
3. Use SEED Docs `discover_seed_docs`, `list_docs`, and `get_doc` for current component behavior.
4. Use SEED Docs `get_rootage` for semantic color, typography, spacing, radius, shadow, and recipe state definitions.
5. Use icon search/details for exact icon names and imports; never guess.
6. Map every UI element to a verified SEED component, a SEED composition, or an explicit custom exception.
7. Check `base`, `md`, and relevant wider layouts, small WebView width, long Korean text, keyboard navigation, large text, and all required states.

### Verified SEED references for this document

- Rootage 2.6.0: `/color.json`, `/font-size.json`, `/font-weight.json`, `/line-height.json`, `/dimension.json`, `/radius.json`, `/shadow.json`
- Rootage recipes: `/components/typography.json`, `/components/action-button.json`, `/components/text-input.json`, `/components/skeleton.json`
- React docs: `components/typography/text`, `components/action-button`, `components/list`, `components/text-field-input`, `components/bottom-sheet`, `components/dialog`
- React docs: `components/chip-tabs`, `components/segmented-control`, `components/skeleton`, `components/result-section`, `components/page-banner`, `components/snackbar`
- React docs: `components/concepts/responsive-design`, `components/layout/grid`

Anything not listed above is a design direction or NalQ decision, not an asserted SEED API. Re-verify at implementation time.
