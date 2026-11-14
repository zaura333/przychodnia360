#!/usr/bin/env python3
"""Plan zadan CI backendu (zadanie 1.9): ktore uslugi zbudowac na podstawie zmienionych plikow.

Wejscie: lista zmienionych sciezek (jedna na linie) na stdin. Wyjscie (stdout, format GITHUB_OUTPUT):
  run=true|false         czy cokolwiek w backendzie wymaga przebiegu
  platform=true|false    czy uruchomic testy bibliotek platform/* (raz, poza macierza uslug)
  services=["a","b"]     uslugi do zbudowania (macierz); puste, gdy run=false

Zasady (sciezki wzgledem korzenia repozytorium):
  * zmiana tylko w backend/services/<usluga>/ buduje tylko te usluge,
  * zmiana w platform/*, wspolnych plikach budowania, kontraktach (api/), dockerze lub pipeline buduje wszystkie
    uslugi i uruchamia testy platformy (usluga zalezy od platform/*, a testy odczytuja te pliki),
  * nieznana sciezka w backend/ jest traktowana jak wspolna (bezpiecznie),
  * zmiany poza backendem (frontend, dokumentacja, inne) nie uruchamiaja nic.
"""
import json
import os
import sys

# Zmiana tych sciezek dotyczy wszystkich uslug i platformy.
GLOBAL_PREFIXES = (
    'backend/platform/',
    'backend/.mvn/',
    'backend/pom.xml',
    'backend/mvnw',
    'backend/mvnw.cmd',
    'backend/Dockerfile',
    'api/',
    'docker/',
    'docker-compose.yml',
    '.java-version',
    '.github/actions/setup-backend/',
    '.github/workflows/backend.yml',
    '.github/scripts/',
    # testy ComposeConsistencyTest czytaja ADR wersji PostgreSQL
    'docs/adr/ADR-B01-postgresql-wersja-i-role.md',
)
SERVICES_PREFIX = 'backend/services/'


def list_services(services_dir):
    """Uslugi to katalogi z pom.xml w backend/services."""
    if not os.path.isdir(services_dir):
        return []
    return sorted(name for name in os.listdir(services_dir)
                  if os.path.isfile(os.path.join(services_dir, name, 'pom.xml')))


def plan(changed, all_services, force_all=False):
    """Zwraca slownik run, platform, services dla listy zmienionych plikow."""
    if force_all:
        return {'run': True, 'platform': True, 'services': list(all_services)}
    global_change = False
    services = set()
    for raw in changed:
        path = raw.strip().replace('\\', '/')
        if not path:
            continue
        if path.startswith(SERVICES_PREFIX):
            name = path[len(SERVICES_PREFIX):].split('/', 1)[0]
            if name in all_services:
                services.add(name)
            # usunieta lub nieznana usluga: nic do zbudowania
            continue
        if any(path == p or path.startswith(p) for p in GLOBAL_PREFIXES):
            global_change = True
        elif path.startswith('backend/'):
            global_change = True
    if global_change:
        return {'run': True, 'platform': True, 'services': list(all_services)}
    if services:
        return {'run': True, 'platform': False, 'services': sorted(services)}
    return {'run': False, 'platform': False, 'services': []}


def main(argv):
    force_all = '--all' in argv
    services_dir = 'backend/services'
    for i, arg in enumerate(argv):
        if arg == '--services-dir' and i + 1 < len(argv):
            services_dir = argv[i + 1]
    changed = [] if force_all else sys.stdin.read().splitlines()
    result = plan(changed, list_services(services_dir), force_all)
    print('run=' + ('true' if result['run'] else 'false'))
    print('platform=' + ('true' if result['platform'] else 'false'))
    print('services=' + json.dumps(result['services'], separators=(',', ':')))


if __name__ == '__main__':
    main(sys.argv[1:])
