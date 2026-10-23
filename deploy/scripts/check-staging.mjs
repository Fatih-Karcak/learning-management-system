const base = process.env.LMS_STAGING_URL || "http://127.0.0.1:18088";
const checks = [
  ["/", 200],
  ["/healthz", 200],
  ["/api/v1/courses/discover?size=1", 200],
  ["/api/v1/internal/courses", 404],
  ["/actuator/prometheus", 404],
];
let failed = false;
for (const [path, status] of checks) {
  try {
    const response = await fetch(new URL(path, base), {
      signal: AbortSignal.timeout(10000),
      redirect: "manual",
    });
    if (response.status !== status)
      throw new Error(`HTTP ${response.status}; beklenen ${status}`);
    if (path.startsWith("/api/v1/courses/discover")) {
      const result = await response.json();
      if (!result.success || !Array.isArray(result.data?.content))
        throw new Error("Katalog yanıtı geçersiz");
    }
    console.log(`OK ${path}: ${status}`);
  } catch (error) {
    failed = true;
    console.error(`FAIL ${path}: ${error.message}`);
  }
}
process.exitCode = failed ? 1 : 0;
