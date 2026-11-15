#!/bin/sh
# Jednorazowe kopiowanie danych wydzielonych modułów (S1 katalog, S2 pacjenci, S3 konta i placówki) z bazy głównej
# clinic-core (dziś scheduling) do baz usług (p360_catalog, p360_patient, p360_iam). Dotyczy środowisk, które mają dane w starych tabelach
# (service_categories, services, patients, tenants, users, tokeny) sprzed wydzielenia; środowiska z danymi z seedu można po prostu zresetować (docker compose down -v).
#
# Dla S3 uruchom iam pierwszy raz z SEED_ENABLED=false (inaczej seed iam założy placówki z innymi identyfikatorami, a
# skrypt odmówi nadpisania). Migracje V12 i V13 w scheduling odmawiają usunięcia tabel, które mają wiersze, więc kolejność jest taka:
#   1. uruchom bazy i usługi catalog oraz patient (ich migracje tworzą tabele docelowe),
#   2. uruchom ten skrypt (kopiuje, sprawdza liczności; dodaj CLEAR_SOURCE=yes, żeby opróżnić tabele źródłowe),
#   3. uruchom scheduling: migracje V12 i V13 usuną puste tabele.
#
# Uruchomienie z korzenia repozytorium (skrypt działa wewnątrz kontenera postgres jako superużytkownik, który omija RLS):
#   docker compose exec -T postgres sh -s < docker/scripts/copy-extracted-data.sh
#   docker compose exec -T -e CLEAR_SOURCE=yes postgres sh -s < docker/scripts/copy-extracted-data.sh
#
# Bezpieczeństwo: skrypt nie nadpisuje danych usług. Gdy tabela docelowa ma już wiersze, a liczność źródła jest inna,
# kończy się błędem; gdy liczności są równe, uznaje kopiowanie za wykonane wcześniej.
set -eu

USER_NAME="${POSTGRES_USER:-postgres}"
SOURCE_DB="${P360_DB_NAME:-p360}"

count() { # <baza> <tabela>
  psql -U "$USER_NAME" -d "$1" -tAc "SELECT count(*) FROM public.$2"
}

exists() { # <baza> <tabela>
  [ "$(psql -U "$USER_NAME" -d "$1" -tAc "SELECT to_regclass('public.$2') IS NOT NULL")" = "t" ]
}

copy_table() { # <tabela> <baza docelowa>
  table="$1"
  target="$2"
  if ! exists "$SOURCE_DB" "$table"; then
    echo "Pominięto $table: nie ma jej w $SOURCE_DB (V12 już wykonana)"
    return 0
  fi
  if ! exists "$target" "$table"; then
    echo "BŁĄD: w $target nie ma tabeli $table. Uruchom najpierw usługę (jej migracje tworzą tabele)." >&2
    return 1
  fi
  src_n=$(count "$SOURCE_DB" "$table")
  if [ "$src_n" -eq 0 ]; then
    echo "$table: źródło puste (brak danych albo przeniesione wcześniej), nic do kopiowania"
    return 0
  fi
  dst_n=$(count "$target" "$table")
  if [ "$dst_n" -gt 0 ]; then
    if [ "$dst_n" -eq "$src_n" ]; then
      echo "$table: w $target jest już $dst_n wierszy (tyle co w źródle), pomijam kopiowanie"
      return 0
    fi
    echo "BŁĄD: w $target.$table jest $dst_n wierszy, a w źródle $src_n. Nie nadpisuję danych usługi." >&2
    return 1
  fi
  pg_dump -U "$USER_NAME" -d "$SOURCE_DB" --data-only --table="public.$table" \
    | psql -U "$USER_NAME" -d "$target" -v ON_ERROR_STOP=1 --single-transaction -q > /dev/null
  dst_n=$(count "$target" "$table")
  if [ "$dst_n" -ne "$src_n" ]; then
    echo "BŁĄD: po kopiowaniu $table: źródło $src_n, cel $dst_n wierszy" >&2
    return 1
  fi
  echo "$table: skopiowano $dst_n wierszy do $target (liczności zgodne)"
}

# kategorie przed usługami (klucz obcy wewnątrz katalogu)
copy_table service_categories p360_catalog
copy_table services p360_catalog
copy_table patients p360_patient
# placówki przed kontami, konta przed tokenami (klucze obce wewnątrz iam)
copy_table tenants p360_iam
copy_table users p360_iam
copy_table refresh_tokens p360_iam
copy_table password_reset_tokens p360_iam

if [ "${CLEAR_SOURCE:-no}" = "yes" ]; then
  # jedno polecenie TRUNCATE: PostgreSQL nie pozwala opróżnić tabeli, do której odwołuje się klucz obcy, osobno
  tables=""
  for table in services service_categories patients password_reset_tokens refresh_tokens users tenants; do
    if exists "$SOURCE_DB" "$table"; then
      tables="${tables:+$tables, }public.$table"
    fi
  done
  if [ -n "$tables" ]; then
    psql -U "$USER_NAME" -d "$SOURCE_DB" -v ON_ERROR_STOP=1 -q -c "TRUNCATE $tables"
    echo "Opróżniono źródło: $tables"
  fi
  echo "Gotowe: scheduling z migracjami V12 i V13 usunie puste tabele przy starcie."
else
  echo "Skopiowano. Źródło nietknięte: uruchom ponownie z CLEAR_SOURCE=yes po sprawdzeniu danych w usługach."
fi
