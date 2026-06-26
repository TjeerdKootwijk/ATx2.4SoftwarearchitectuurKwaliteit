#!/usr/bin/env python3
"""
Meet de CONSUMER-doorvoer van de notificatie-pipeline (berichten/sec die de
consumer via de provider verstuurt) — los van HTTP-backpressure.

Werkwijze:
  1. Lees de huidige stand van notifications_sent_total (success+failed).
  2. Zet snel een vaste backlog (N berichten) in de queue via POST /test.
  3. Poll de RabbitMQ queue-diepte en de sent-metric tot de queue leeg is.
  4. Rapporteer de gemiddelde en piek-drain rate (msg/s).

Gebruik dit zowel vóór als ná een concurrency-wijziging om de winst te tonen:

    python measure_drain.py --backlog 3000
    python measure_drain.py --backlog 3000 --label "na fix (concurrency=8)"
"""

import argparse
import random
import threading
import time
import urllib3

import requests

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

PROVIDERS = ["SWIFTSEND", "SECUREPOST", "LEGACYLINK", "ASYNCFLOW"]


def read_sent_total(prom_url):
    """Som van notifications_sent_total over alle providers/statussen."""
    r = requests.get(prom_url, verify=False, timeout=5)
    total = 0.0
    for line in r.text.splitlines():
        if line.startswith("notifications_sent_total"):
            total += float(line.rsplit(" ", 1)[1])
    return total


def read_queue_depth(api_base, queue):
    """Aantal 'ready' berichten in de queue via de RabbitMQ management API."""
    r = requests.get(f"{api_base}/api/queues/%2F/{queue}", auth=("guest", "guest"), timeout=5)
    if r.status_code != 200:
        return None
    data = r.json()
    return data.get("messages_ready", 0), data.get("messages_unacknowledged", 0)


def flood(base_url, count):
    """Zet 'count' berichten zo snel mogelijk in de queue."""
    sent = [0]
    lock = threading.Lock()

    def worker(n):
        s = requests.Session()
        for _ in range(n):
            row_provider = random.choice(PROVIDERS)
            try:
                s.post(base_url, params={"providerType": row_provider,
                                         "tenantId": f"tenant-{random.randint(1, 4)}",
                                         "recipient": "+31600000000"},
                       verify=False, timeout=(10, 30))
                with lock:
                    sent[0] += 1
            except requests.RequestException:
                pass

    n_threads = 20
    per = count // n_threads
    threads = [threading.Thread(target=worker, args=(per,), daemon=True) for _ in range(n_threads)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return sent[0]


def main():
    parser = argparse.ArgumentParser(description="Meet consumer-drain rate van de pipeline")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--protocol", default="https")
    parser.add_argument("--rabbit-api", default="http://localhost:15672")
    parser.add_argument("--queue", default="notification.queue")
    parser.add_argument("--backlog", type=int, default=3000, help="aantal berichten in de backlog")
    parser.add_argument("--max-wait", type=int, default=600, help="max seconden wachten op leeglopen")
    parser.add_argument("--label", default="baseline")
    args = parser.parse_args()

    base_url = f"{args.protocol}://{args.host}:{args.port}/api/notifications/test"
    prom_url = f"{args.protocol}://{args.host}:{args.port}/actuator/prometheus"

    print("=" * 60)
    print(f"  Consumer-drain meting — {args.label}")
    print("=" * 60)

    start_sent = read_sent_total(prom_url)
    print(f"  Start sent-metric : {start_sent:.0f}")
    print(f"  Backlog vullen    : {args.backlog} berichten...")
    flood_start = time.monotonic()
    enqueued = flood(base_url, args.backlog)
    flood_dur = time.monotonic() - flood_start
    print(f"  Geenqueued        : {enqueued} in {flood_dur:.1f}s ({enqueued / flood_dur:.0f} req/s)")
    print(f"  Wachten tot de queue leeg is...\n")

    drain_start = time.monotonic()
    prev_sent = read_sent_total(prom_url)
    prev_t = drain_start
    peak_rate = 0.0

    while True:
        time.sleep(5)
        now = time.monotonic()
        cur_sent = read_sent_total(prom_url)
        depth = read_queue_depth(args.rabbit_api, args.queue)
        interval_rate = (cur_sent - prev_sent) / (now - prev_t)
        peak_rate = max(peak_rate, interval_rate)
        ready, unacked = depth if depth else ("?", "?")
        print(f"  t+{now - drain_start:5.0f}s | verwerkt={cur_sent - start_sent:6.0f} | "
              f"rate={interval_rate:6.1f}/s | queue ready={ready} unacked={unacked}")
        prev_sent, prev_t = cur_sent, now

        if depth and depth[0] == 0 and depth[1] == 0:
            break
        if now - drain_start > args.max_wait:
            print("  ! max-wait bereikt — afgebroken")
            break

    total_drained = read_sent_total(prom_url) - start_sent
    total_time = time.monotonic() - drain_start
    avg_rate = total_drained / total_time if total_time else 0.0

    print()
    print("=" * 60)
    print(f"  RESULTAAT — {args.label}")
    print("=" * 60)
    print(f"  Verwerkt          : {total_drained:.0f} berichten")
    print(f"  Drain-tijd        : {total_time:.1f}s")
    print(f"  Gem. drain rate   : {avg_rate:.1f} msg/s")
    print(f"  Piek drain rate   : {peak_rate:.1f} msg/s")
    print("=" * 60)


if __name__ == "__main__":
    main()
