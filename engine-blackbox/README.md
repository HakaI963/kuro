# engine-blackbox module

Real adapter implementing `:engine`'s `VirtualEngine` on top of the vendored
BlackBox core (`:blackbox`, see `third_party/NewBlackbox` and `docs/ENGINE.md`).

- `BlackBoxEngine`: install/launch/list/remove via `BlackBoxCore` user slots.
  One Kuro clone = one engine user (`cloneId` is `"u<userId>"`).
- `BlackBoxBootstrap`: `attachBaseContext`/`onCreate` wiring mirroring the
  engine's own sample app; failures are recorded, never thrown.
- `BLACKBOX_UPSTREAM_COMMIT`: pinned upstream commit for diagnostics display.

No engine logic is reimplemented here; this module only adapts real
`BlackBoxCore` API calls to the `VirtualEngine` interface with honest errors.
