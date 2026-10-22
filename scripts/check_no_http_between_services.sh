#!/usr/bin/env bash
# NFR-ARCH-02: services talk to each other over gRPC only. Fails if an HTTP client appears in a
# service, or in the gateway outside the artwork package (the only place HTTP bytes are served).
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"; cd "$root"
pattern='RestTemplate|WebClient|RestClient|HttpClient|java\.net\.http|HttpURLConnection|OpenFeign'
hits=$(grep -rEn "$pattern" movie-service/src people-service/src api-gateway/src/main 2>/dev/null \
  | grep -v '/artwork/' || true)
if [ -n "$hits" ]; then echo "FAIL: HTTP client usage found:"; echo "$hits"; exit 1; fi
echo "OK: no HTTP clients between services"
