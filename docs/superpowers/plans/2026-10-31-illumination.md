# Illumination implementation plan
Goal: official five-option LED control, with readback confirmation, all three platforms.
Spec: ../specs/2026-10-31-illumination.md
- Add failing Python tests for parsing, unsupported data, allowed values, confirmed write/readback, unchanged confirmation and stale-read rejection. Implement bounded private-IP GET helper; separate read polling with generation guard; allow only IL1..IL5 and confirm actual returned value.
- Add failing Java parser/command tests; implement equivalent ReceiverHttp API and background monitor callbacks. Integrate freshness-aware Android selector and Windows/web select. Never change DisplaysBrightness.
- Add browser checks for labels, stale/unsupported disablement, and selected reported value. Validate actual unchanged Auto readback on hardware, Python/Java/browser suites; update version to1.4, release notes/build outputs; fresh review and publish tested builds.
