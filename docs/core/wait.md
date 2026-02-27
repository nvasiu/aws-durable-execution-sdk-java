## wait() – Suspend Without Cost

Waits suspend the function and resume after the specified duration. You're not charged during suspension.

```java
// Wait 30 minutes
ctx.wait(Duration.ofMinutes(30));

// Named wait (useful for debugging)
ctx.wait("cooling-off-period", Duration.ofDays(7));
```