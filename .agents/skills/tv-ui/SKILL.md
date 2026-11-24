---
name: tv-ui
description: Native Android TV interaction, focus, layout and accessibility.
---
# TV UI
- Follow current Android Developers TV and Compose for TV guidance. Prefer latest compatible stable native components.
- Design for ten-foot readability, responsive layouts, clear focus, D-pad navigation, Back behavior and accessible localized labels.
- Trace key down/up, repeat and long press; distinguish focus, pointer, touch and media keys where supported. Restore logical focus after dialogs, transitions and list refresh; never default to destructive actions.
- Avoid unnecessary recomposition, continuous animations and heavy assets. Preserve scroll and focus state.
- Render changed screens at 720p and 1080p, check scaling for 4K, and inspect focused states. Include RTL and other supported form factors when relevant.
- Use actual input tests alongside screenshots; screenshots alone do not validate interaction. Avoid claiming device validation without a device run.

## AMOLED TV UI
Mandatory native Jetpack Compose for TV and AndroidX TV Material, latest compatible stable versions verified against official releases. Pure black #000000 window and content background; #121212 surfaces, #80DCC0 mint accent, #F2F4F3 primary text. Centralize tokens in VireoTheme; preserve native TV focus, typography and D-pad behavior. No mobile-only Material components or unnecessary UI libraries. Verify startup, focus contrast, back navigation and accessibility. Theme source introduced in 147e72c; build and visual acceptance remain unverified.
