# Wiki source patches

Store a `<command>.json` file here only when a small, manually reviewed edit to
the cached Wiki page is needed before command data is extracted. A patch file
uses version `1`, a top-level `reason`, and an ordered `replacements` array. Each
replacement contains exact `old` and `new` text; `expected_matches` defaults to
`1` and can be set when the same source passage should occur more than once.
Replacement-specific `reason` is optional.

Keep patches small and tied to source passages. The engine normalizes line
endings to LF in its in-memory working text, requires every `old` passage to
match the stated number of times at that point in the ordered patch sequence,
and fails with the command, patch file, and replacement number if the Wiki page
has drifted. It never edits the cached Wiki source file. Review and update the
patch deliberately when upstream wording changes.

Patches are deltas, not alternate command documentation. Do not replace or
rewrite a whole page with AI-generated text. Preserve fixed enumeration
introductions such as `必须为以下其中之一：` so the extractor can recognize the
following option list. For a Boolean, `description` contains its purpose and
shared conditions; `descriptionTrue` and `descriptionFalse` contain each state's
meaning. Never copy the value list into the parent description or use it to
replace a missing purpose. Following the maintainer's 2026-10-29 update, remove
prose describing omitted or unspecified arguments, including default-state,
failure and query cases, through source patches. Keep explicit input-value
effects when a source sentence combines them with omission behavior. Keep the
parameter purpose complete and leave the optional syntax structure unchanged.
Also remove runtime prose about legal ranges, bounds, invalid-input fallback or
clamping, and format or target-validity restrictions (maintainer update, 2026-10-29).
Keep purposes, units, directions, explicit option meanings and the context needed
to distinguish syntax branches. This is a prose change: retain all validation
metadata and brief labels. Fixed enum introductions still belong in source text.
The maintainer's subsequent update also removes default-value notes from runtime
descriptions: standalone defaults, numeric default values and default-option tags.
Keep the purpose, units and each option's meaning. Preserve actual identifiers
named `default` and reset operations; remove annotations rather than these values
or actions. Default metadata and execution behavior stay unchanged.
The subsequent brevity review keeps parameter prose to a short purpose and
necessary units. Remove exceptions, special-input side cases, examples, long
lists and secondary effects, including previously retained zero/-1 notes. Keep
each Boolean or enum value's core meaning and the minimum syntax context needed
for a complete purpose. Do not truncate by length or blindly take the first
sentence. Existing execute structure and nodes remain fixed; its root synopsis
is maintained in the existing fixed patch.
Do not repeat type/default-value templates. A final `brief:` line may override the
automatically selected brief label when a parameter name alone is too long or
unclear.

Prefer the source's facts and wording. A patch may reorganize existing facts into
shorter passages, but shortening is not a reason to translate parameter identifiers
or replace clear Chinese Wiki terminology. For example, preserve
`指定测试的旋转角度。` rather than rewriting it as `指定测试结构的旋转步数。`.
Retain an already suitable purpose sentence verbatim whenever possible.
Following the maintainer's corrected 2026-10-30 terminology guidance, prefer
Chinese Wiki terminology, then established domain terminology. For example,
retain the Wiki's `指定效果的放大倍率。` rather than replacing it with a level
description. A different game-language name alone does not make a Wiki term
incorrect. Correct actual semantic mismatches or missing context, recording
the evidence in the patch reason; do not mechanically standardize synonyms.
Game Chinese language entries may corroborate a meaning, but do not import
official-document or English-Wiki prose. Brief labels may be corrected for a
confirmed terminology issue while identifiers and numeric semantics stay unchanged.
A patch may also reorganize existing facts into
option items or expand an explicitly stated opposite Boolean state, with its
reason recording the original passage. For missing explanations, the maintainer
has explicitly authorized locally authored supplements (2026-10-15). Add concise,
reviewed Chinese prose through minimal patch replacements and label each such
replacement's `reason` as `人工补充`, explaining its meaning and scope. These are
local maintenance text, not quotations from the Wiki. The page provenance report
exports `patch_reason` and ordered `replacement_reasons` alongside the raw and
patched hashes so that this distinction remains visible. Keep the downloaded
`page.wiki` unchanged. Prefer a minimal patch to natural parameter headings,
aliases, edition/context headings, option lists or syntax notes when extraction
needs clarification. Keep related purpose sentences and their conditions together;
use a shared parameter heading only when the inputs describe the same concept.
Shared source paragraphs are not proof that distinct inputs are aliases. Split
natural parameter definitions when each input has its own role, such as RGB
components, individual rotation axes or fade/hold durations. Keep group purposes
and combined behavior on the corresponding group or keyword, and retain each
input's actual limits and conditions. Do not split a single POSITION input into
new nodes or invent distinctions between inputs with the same role.
Do not embed resource JSON pointers, line selectors or an equivalent binding
language in the Wiki text. Source maintenance uses only these patches: matching
is derived from the actual command syntax, typed parameter names and natural
Wiki sections. External ID documentation is derived from command ID references
and the associated parameter options, without a separate registry.

The natural statement `本命令不支持的值：<code>value</code>。` records an explicitly
unsupported enum value. Only values documented this way may be excluded from an
ID catalog; a newly encountered value without source text still blocks generation.
Natural definitions can explicitly label a command word (`; 关键字 <code>list</code>`)
or fixed value (`; 固定值 <code>*</code>`). Ordinary untyped Wiki argument headings
remain arguments; their appearance alone must not turn them into command words.
Do not include release chronology or experimental-feature introduction/removal
history in runtime descriptions. Keep that evidence in patch reasons and source
history instead.

External prose sources remain limited to Chinese Minecraft Wiki, including
documented Chinese historical pages. English Wiki and official-documentation
prose are not permitted fallback sources for the authorized supplements either.
Archive the original quoted context and URL when a patch imports a fact from a
different Chinese Wiki page. Preserve version and condition scope when authoring
missing prose, and have Luna review the actual patched page and extracted JSON.

When an alias resolves to a canonical command page, its own patch has priority.
Without an alias-specific file, the loader reuses the canonical command's patch
using cached `resolved_title`; exact match counts still apply to the alias's
actual cached page. Source reports name the patch file that was used.
