#!/usr/bin/env python3
"""
End-to-end pipeline performance meting.

Meet de VOLLEDIGE pipeline, gevoed via de OpenMRS-ingest-kant:

  PollingJob -> fake-openmrs -> FHIR-mapping -> FHIR-validatie -> event
             -> AppointmentService -> RabbitMQ -> consumer -> provider

In tegenstelling tot loadtest.py (die alleen de verzend-helft via /test raakt),
wordt hier de hele keten belast: de PollingJob haalt N nep-afspraken op uit
fake-openmrs en duwt ze door FHIR-validatie en de rest van de pipeline.

Het script meet twee snelheden:
  - INGEST  : hoe snel de queue zich vult (poll + FHIR + publiceren)
  - VERZEND : hoe snel de consumer de berichten via de provider verstuurt

Werkwijze (zie performance/README.md voor het volledige recept):
  1. Zet FAKE_APPOINTMENT_COUNT_1H hoog in .env
  2. Leeg processed_events + de queue, herstart fake-openmrs + app
  3. Draai dit script; het kijkt naar de metric en de wachtrij over tijd
"""

import argparse
import time
import urllib3

import requests

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def sent_total(prom_url):
    """Som van notifications_sent_total; None als de app (nog) niet bereikbaar is."""
    try:
        r = requests.get(prom_url, verify=False, timeout=5)
    except requests.RequestException:
        return None
    total = 0.0
    for line in r.text.splitlines():
        if line.startswith("notifications_sent_total"):
            total += float(line.rsplit(" ", 1)[1])
    return total


def queue_depth(api_base, queue):
    try:
        r = requests.get(f"{api_base}/api/queues/%2F/{queue}", auth=("guest", "guest"), timeout=5)
    except requests.RequestException:
        return 0, 0
    if r.status_code != 200:
        return 0, 0
    d = r.json()
    return d.get("messages_ready", 0), d.get("messages_unacknowledged", 0)


def wait_for_app(prom_url, max_wait=120):
    """Wacht tot de app de metrics-endpoint serveert (na een herstart)."""
    deadline = time.monotonic() + max_wait
    while time.monotonic() < deadline:
        v = sent_total(prom_url)
        if v is not None:
            return v
        time.sleep(2)
    raise SystemExit("App niet bereikbaar — draait de stack en is de app gezond?")


def main():
    p = argparse.ArgumentParser(description="End-to-end pipeline meting")
    p.add_argument("--host", default="localhost")
    p.add_argument("--port", type=int, default=8443)
    p.add_argument("--protocol", default="https")
    p.add_argument("--rabbit-api", default="http://localhost:15672")
    p.add_argument("--queue", default="notification.queue")
    p.add_argument("--expected", type=int, default=0, help="verwacht aantal notificaties (0 = onbekend)")
    p.add_argument("--max-wait", type=int, default=300)
    p.add_argument("--label", default="end-to-end pipeline")
    args = p.parse_args()

    prom_url = f"{args.protocol}://{args.host}:{args.port}/actuator/prometheus"

    print("=" * 60)
    print(f"  End-to-end pipeline meting — {args.label}")
    print("=" * 60)
    print("  Wachten tot de app online is (na herstart)...")
    start_sent = wait_for_app(prom_url)
    print("  App online — wachten op de polling-cyclus...")

    t0 = time.monotonic()
    prev_sent = start_sent
    prev_t = t0
    peak_queue = 0
    peak_send_rate = 0.0
    first_activity_t = None
    idle_rounds = 0

    while True:
        time.sleep(3)
        now = time.monotonic()
        cur_sent = sent_total(prom_url)
        if cur_sent is None:
            continue  # app even niet bereikbaar; volgende ronde opnieuw
        ready, unacked = queue_depth(args.rabbit_api, args.queue)
        in_flight = ready + unacked
        peak_queue = max(peak_queue, in_flight)

        send_rate = (cur_sent - prev_sent) / (now - prev_t)
        peak_send_rate = max(peak_send_rate, send_rate)
        processed = cur_sent - start_sent

        if first_activity_t is None and (in_flight > 0 or processed > 0):
            first_activity_t = now - t0
            print(f"  [t+{first_activity_t:.0f}s] eerste activiteit — pipeline ingest gestart")

        print(f"  t+{now - t0:5.0f}s | verwerkt={processed:5.0f} | verzend-rate={send_rate:5.1f}/s "
              f"| queue(ready={ready} unacked={unacked})")

        prev_sent, prev_t = cur_sent, now

        # Tel achtereenvolgende "stille" rondes (queue leeg én niets verstuurd).
        if first_activity_t is not None and in_flight == 0 and send_rate == 0:
            idle_rounds += 1
        else:
            idle_rounds = 0

        if args.expected and processed >= args.expected:
            break
        # Pas stoppen na meerdere stille rondes, zodat een korte dip niet voortijdig afbreekt.
        if idle_rounds >= 3:
            break
        if now - t0 > args.max_wait:
            print("  ! max-wait bereikt")
            break

    elapsed = time.monotonic() - t0
    processed = sent_total(prom_url) - start_sent

    print()
    print("=" * 60)
    print(f"  RESULTAAT — {args.label}")
    print("=" * 60)
    print(f"  Notificaties verwerkt : {processed:.0f}")
    print(f"  Totale tijd           : {elapsed:.1f}s")
    if first_activity_t is not None:
        print(f"  Ingest-latency        : {first_activity_t:.0f}s (poll -> eerste bericht in queue)")
    print(f"  Piek queue-diepte     : {peak_queue}  (hoe ver ingest op verzenden vooruitliep)")
    print(f"  End-to-end throughput : {processed / elapsed:.1f} msg/s")
    print(f"  Piek verzend-rate     : {peak_send_rate:.1f} msg/s")
    print("=" * 60)
    print("  Tip: een hoge piek queue-diepte = ingest is veel sneller dan verzenden")
    print("  (de consumer/provider is dan de bottleneck — zie RAPPORT.md).")


if __name__ == "__main__":
    main()
