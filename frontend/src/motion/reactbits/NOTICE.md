# React Bits components used by CaiZhiTong

Source: [DavidHDev/react-bits](https://github.com/DavidHDev/react-bits), commit `4dc7b5b7772e1217c8fb8d2a7aa074302180e5b4`, retrieved 2026-11-05.

ParticleText, CircularText, TextType, CurvedLoop and DitherVeil are copied from `src/ts-default/`, with the original [MIT + Commons Clause notice](LICENSE.md) retained. These vendored files retain that license; the project's MIT license does not relicense them. They are used as part of this application, not offered as a standalone component distribution.

Local integration: plain CSS / TypeScript; no Tailwind or shadcn framework added. DitherVeil's remote portrait fallback is replaced with the project's own local vector material mark, pixel ratio is capped at 1.5, and its canvas remains transparent until the image loads (or if the graphics context is lost). Login effects are desktop-only, lazy loaded, stop while the page is hidden, and use static fallbacks for reduced motion or unavailable WebGL. ParticleText's successful sampling is marked for a visible initial text fallback; its bounded sample budget is increased for short Chinese headings. Additional local fixes are recorded in the UI polish development log.

The navigation and drawer transitions take visual inspiration from [Staggered Menu](https://reactbits.dev/components/staggered-menu); they preserve Ant Design's navigation, focus, overlay and permission behavior rather than copying its marketing menu layout.
