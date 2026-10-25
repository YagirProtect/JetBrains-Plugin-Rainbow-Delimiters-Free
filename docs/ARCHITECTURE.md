# Code structure

The plugin has two IntelliJ entry points in `dev.yaro.rainbowbraces`: `RainbowBracesEditorListener` and `RainbowSettingsConfigurable`. They connect platform callbacks to the components below.

| Package | Responsibility |
| --- | --- |
| `scanning` | Find delimiters and matching pairs using the IDE lexer or the manual fallback. It does not create highlights or change settings. |
| `editor` | Own editor listeners, the document scan cache, bracket highlights and indent guide rendering. |
| `settings` | Validate and persist preferences, migrate existing XML settings, provide read-only snapshots. |
| `palette` | Represent palettes, edit unsaved palette copies, display palette controls, import/export JSON. |

## Editor lifecycle

`RainbowBracesEditorListener` creates one `RainbowEditorSession` per supported editor. The session owns its event listeners and a debounced Swing alarm. `RainbowHighlighterManager` owns the highlights added by this plugin. Closing an editor disposes the session and its children, restores any wrapped native renderers and removes plugin highlights.

`DelimiterScanCache` reuses the scanner result until the document modification stamp, palette size or file extension changes. Caret movement updates matching-pair emphasis and repaints guides without invalidating that cache. Scrolling updates the visible highlighters.

`RainbowIndentGuideRenderer` draws colored guides, including the space occupied by Code Vision block inlays. `RainbowNativeGuideController` temporarily wraps native guide renderers in both editor and document markup so the corresponding gray line is not painted twice. Document markup is shared by split editors; its wrapper tracks each editor's replacement independently and restores the original renderer after the last controller detaches. Unrelated native guides remain visible. The version-specific class-name check is isolated in that controller.

For C-family files, the delimiter scanner also feeds code characters to `SwitchCaseScanner`. It uses matched delimiter ranges to distinguish switch sections from nested blocks, patterns, strings and comments, then returns immutable `SwitchCaseSection` ranges. `SwitchCaseGuideRenderer` draws ordinary theme-colored guides for sections without braces. Both guide renderers share `IndentGuidePainting` for clipping, text avoidance, block inlays and device-pixel alignment, and implement `NativeGuideReplacement` to prevent duplicate native lines.

## Settings and encapsulation

`RainbowSettingsService.preferences()` returns a prepared snapshot. Scalar properties use `val`, palette objects are immutable, and collection getters return unmodifiable copies or lists. `activeColors()` returns a copy of the cached color array.

Change preferences through `updatePreferences(snapshot.copy(...))`. Validation and normalization run at this update boundary, rather than during reads. The settings page also refreshes open editors after Apply.

`RainbowSettingsState` and `RainbowPaletteState` are mutable XML serialization beans, not the live settings model. Their public `var` properties are needed by IntelliJ's serializer. `getState()` creates fresh beans; `loadState()` converts incoming beans into validated, detached values. The existing component name, XML option names and storage filename remain compatible with earlier versions.

`lastSavedPluginVersion` records which plugin build last saved the settings. Gradle expands `META-INF/rainbow-delimiters.properties` from the same project version used in the plugin descriptor; `PluginBuildInfo` reads that resource without requiring an initialized IDE. `loadState()` captures the incoming version in the read-only `loadedFromPluginVersion` property before any future migration decisions. `getState()` stamps the current build on its detached output without replacing that incoming version. An absent or blank version means unknown legacy provenance. Version tracking alone does not migrate or expand extension selections.

`PaletteEditorModel` owns unsaved palette edits. Its explicit operations cover selection, duplication, rename, color changes, reset, import and deletion. Editing the settings page cannot change application settings until Apply; Reset discards those edits.

`PaletteColorAdjustment` applies relative HSB saturation and brightness to raw palette colors. Values of 100% preserve the exact original RGB values. The service caches adjusted `JBColor` instances with separate light/dark variants. `PaletteEditorPanel` uses the same transform directly on every palette swatch, updating previews without rebuilding controls while a slider moves. The settings service and open editors are updated only by Apply. Color adjustments never overwrite palette data or exported JSON.

## Palette files

`PaletteJsonCodec` uses the platform's Gson library. It reads previous version-1 exports without depending on JSON field order or manual string escaping. The optional `darkColors` field preserves different light/dark colors; files containing only `colors` use those values for both themes.

## Tests

Tests mirror these responsibilities: scanner, cache/editor lifecycle, native guide replacement, guide rendering, settings UI, settings isolation/migration, palette editing and JSON interchange. `RainbowEditorTestCase` contains shared IntelliJ editor fixtures; the remaining model tests do not need an editor fixture.

Run `./gradlew test` on the default IntelliJ IDEA target. See the README for binary compatibility verification commands and alternate IDE targets.
