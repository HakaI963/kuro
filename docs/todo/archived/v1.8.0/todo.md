---
- id: T12
  title: Cancelled-by-user state not reset on new enqueue
  type: bug
  priority: low
  difficulty: easy
  frequency: rare
  expected: After cancelling an install, starting a new install shows "Install in progress" notification
  actual: After cancelling, a new install's notification body shows "Cancelled by user" during active install
  reproduction: |
    1. Start any component install
    2. Cancel it from the UI
    3. Start a different component install
    4. Check notification — body shows "Cancelled by user" instead of "Install in progress"
  impact: Notification text only; install functionality is unaffected
  images: null
  github_ref: null
  plan: |
    In InstallationQueueManager.enqueue(), add `cancelledByUser = false` to the
    _installState.value.copy() call so the flag resets when a new task is enqueued.
---
