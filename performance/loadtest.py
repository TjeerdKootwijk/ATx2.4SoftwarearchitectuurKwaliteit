#!/usr/bin/env python3
"""
Performance / load test voor de notificatie-pipeline.

Belast POST /api/notifications/test over meerdere tenants en providers met een
configureerbaar aantal gelijktijdige workers gedurende een vaste tijd. Elke
request zet een bericht in RabbitMQ; de consumer verstuurt het via de provider.

Lichte Python-variant van het JMeter-plan: geen externe install nodig behalve
`requests` (al gebruikt elders in deze repo). Self-signed TLS wordt genegeerd.

Gebruik (vanuit de performance/ map):

    python loadtest.py
    python loadtest.py --threads 200 --duration 300
    python loadtest.py --host localhost --port 8443 --csv tenants-providers.csv

Bekijk de doorvoer van de rest van de keten in RabbitMQ (http://localhost:15672)
en Grafana (http://localhost:3000) — dit script meet tot "in queue" (HTTP 202).
Als de wall-clock-tijd flink boven --duration uitkomt, blokkeert de broker de
publishers (backpressure): de consumer/provider-kant is dan de bottleneck.
"""

import argparse
import csv
import os
import random
import threading
import time
import urllib3
from collections import Counter

import requests

# Self-signed certificaat van de app: validatie uitzetten + waarschuwing dempen.
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def load_rows(csv_path):
    """Lees tenant/provider/recipient-combinaties uit de CSV."""
    with open(csv_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise SystemExit(f"Geen rijen gevonden in {csv_path}")
    return rows


def percentile(sorted_values, pct):
    """Eenvoudige percentiel (nearest-rank) op een gesorteerde lijst in ms."""
    if not sorted_values:
        return 0.0
    k = max(0, min(len(sorted_values) - 1, int(round(pct / 100.0 * len(sorted_values) + 0.5)) - 1))
    return sorted_values[k]


# Resultaten verzameld door alle workers. Incrementeel onder lock bijgewerkt, zodat
# threads die aan het eind nog vastzitten op een geblokkeerde publish geen al
# verzamelde data verliezen.
latencies = []          # in ms, alleen geslaagde requests
status_counts = Counter()
provider_counts = Counter()
errors = Counter()
results_lock = threading.Lock()


def worker(stop_at, base_url, rows, thread_local, timeouts):
    session = getattr(thread_local, "session", None)
    if session is None:
        session = requests.Session()
        thread_local.session = session

    while time.monotonic() < stop_at:
        row = random.choice(rows)
        params = {
            "providerType": row["provider"],
            "tenantId": row["tenantId"],
            "recipient": row["recipient"],
        }
        t0 = time.perf_counter()
        try:
            resp = session.post(base_url, params=params, verify=False, timeout=timeouts)
            dt = (time.perf_counter() - t0) * 1000.0
            with results_lock:
                status_counts[resp.status_code] += 1
                if resp.status_code == 202:
                    latencies.append(dt)
                    provider_counts[row["provider"]] += 1
                else:
                    errors[f"HTTP {resp.status_code}"] += 1
        except requests.RequestException as exc:
            with results_lock:
                errors[type(exc).__name__] += 1


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description="Load test voor de notificatie-pipeline")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--protocol", default="https", choices=["http", "https"])
    parser.add_argument("--threads", type=int, default=50, help="gelijktijdige workers")
    parser.add_argument("--duration", type=int, default=120, help="testduur in seconden")
    parser.add_argument("--connect-timeout", type=float, default=10.0, help="connect-timeout (s)")
    parser.add_argument("--read-timeout", type=float, default=30.0, help="read-timeout per request (s)")
    parser.add_argument("--csv", default=os.path.join(here, "tenants-providers.csv"))
    args = parser.parse_args()

    base_url = f"{args.protocol}://{args.host}:{args.port}/api/notifications/test"
    rows = load_rows(args.csv)
    timeouts = (args.connect_timeout, args.read_timeout)

    print("=" * 60)
    print("  Notificatie-pipeline load test")
    print("=" * 60)
    print(f"  Doel        : {base_url}")
    print(f"  Workers     : {args.threads}")
    print(f"  Duur        : {args.duration}s")
    print(f"  Combinaties : {len(rows)} (tenant x provider)")
    print("=" * 60)
    print("  Bezig... (Ctrl+C om vroegtijdig te stoppen)\n")

    thread_local = threading.local()
    started = time.monotonic()
    stop_at = started + args.duration

    threads = []
    for _ in range(args.threads):
        t = threading.Thread(target=worker, args=(stop_at, base_url, rows, thread_local, timeouts), daemon=True)
        t.start()
        threads.append(t)

    # Harde bovengrens: na de duur geven we de workers nog één read-timeout aan
    # gratie om lopende requests af te ronden, daarna rapporteren we. Threads die
    # dan nog vastzitten op een geblokkeerde publish (backpressure) zijn daemon
    # en sterven bij proces-exit; hun al verzamelde data is veilig (incrementeel).
    grace_deadline = stop_at + args.read_timeout + 5
    interrupted = False
    try:
        for t in threads:
            remaining = grace_deadline - time.monotonic()
            if remaining <= 0:
                break
            t.join(timeout=remaining)
    except KeyboardInterrupt:
        interrupted = True
        print("\nOnderbroken — resultaten tot nu toe:\n")

    elapsed = time.monotonic() - started
    stuck = sum(1 for t in threads if t.is_alive())

    with results_lock:
        network_errors = sum(v for k, v in errors.items() if not str(k).startswith("HTTP"))
        total_requests = sum(status_counts.values()) + network_errors
        accepted = status_counts.get(202, 0)
        lat_sorted = sorted(latencies)
        provider_snapshot = dict(provider_counts)
        error_snapshot = errors.most_common()

    failed = total_requests - accepted
    # Requests worden uitgegeven binnen het duur-venster; completions druppelen na
    # in de gratieperiode. Throughput meten we over het uitgifte-venster.
    window = min(elapsed, args.duration) if not interrupted else elapsed
    avg = sum(lat_sorted) / len(lat_sorted) if lat_sorted else 0.0

    print("=" * 60)
    print("  RESULTAAT")
    print("=" * 60)
    print(f"  Looptijd (wall)   : {elapsed:.1f}s  (ingesteld: {args.duration}s)")
    if elapsed > args.duration * 1.5 or stuck:
        print(f"  ! Backpressure    : {stuck} thread(s) bleven hangen op een trage/")
        print(f"                      geblokkeerde publish -> broker knijpt producers af.")
    print(f"  Totaal requests   : {total_requests}")
    print(f"  Geaccepteerd (202): {accepted}")
    print(f"  Mislukt           : {failed}")
    err_pct = (failed / total_requests * 100.0) if total_requests else 0.0
    print(f"  Error %           : {err_pct:.2f}%")
    print(f"  Throughput        : {total_requests / window:.1f} req/s")
    print()
    print("  Latency (ms, geslaagde requests):")
    print(f"    gemiddeld : {avg:.1f}")
    print(f"    p50       : {percentile(lat_sorted, 50):.1f}")
    print(f"    p90       : {percentile(lat_sorted, 90):.1f}")
    print(f"    p95       : {percentile(lat_sorted, 95):.1f}")
    print(f"    p99       : {percentile(lat_sorted, 99):.1f}")
    print(f"    max       : {lat_sorted[-1]:.1f}" if lat_sorted else "    max       : -")
    print()
    print("  Requests per provider (geaccepteerd):")
    for prov, cnt in sorted(provider_snapshot.items()):
        print(f"    {prov:<12}: {cnt}")
    if error_snapshot:
        print()
        print("  Fouten:")
        for err, cnt in error_snapshot:
            print(f"    {err:<20}: {cnt}")
    print("=" * 60)
    print("  Pipeline-doorvoer (consumer -> provider): zie RabbitMQ")
    print("  http://localhost:15672 (queue notification.queue) en Grafana")
    print("  http://localhost:3000 (metric notifications_sent_total).")
    print("=" * 60)


if __name__ == "__main__":
    main()
